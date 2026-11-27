/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2026 keycloak-config-cli contributors
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.RealmExportParser;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.containers.GenericContainer;

/** The dry run and the import of files on a connected target, against the Keycloak of the integration tests. */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class TargetImportIT {

    private static final GenericContainer<?> KEYCLOAK = AbstractImportIT.KEYCLOAK_CONTAINER;
    private static final String URL = System.getProperty("keycloak.url");
    private static final String ADMIN = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN");
    private static final String ADMIN_PASSWORD = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN_PASSWORD");

    private static final String NEW_REALM = "target-import-new";
    private static final String APPLIED_REALM = "target-import-applied";
    private static final String UNCHANGED_REALM = "target-import-unchanged";
    private static final String CHANGED_REALM = "target-import-changed";
    private static final List<String> REALMS = List.of(NEW_REALM, APPLIED_REALM, UNCHANGED_REALM, CHANGED_REALM);

    private static ConfigurableEnvironment mainEnvironment;

    @BeforeAll
    static void startTheShell() {
        GenericApplicationContext main = new GenericApplicationContext();
        TestPropertyValues.of("run.operation=SHELL").applyTo(main);
        new ConfigDataApplicationContextInitializer().initialize(main);
        mainEnvironment = main.getEnvironment();
        removeTheRealms();
    }

    /** The realms live in the container shared by the other ITs: they are removed. */
    @AfterAll
    static void removeTheRealms() {
        try (Keycloak admin = admin()) {
            admin.realms().findAll().stream()
                    .filter(realm -> REALMS.contains(realm.getRealm()))
                    .forEach(realm -> admin.realm(realm.getRealm()).remove());
        }
    }

    @Test
    void aDryRunOfANewRealmPlansItsCreationAndCreatesNothing() {
        try (Connection connection = connect()) {
            ImportReport report = connection.dryRun(List.of(realmFile(NEW_REALM, "https://app.example.org/*")));

            List<PlannedChange> plan = report.plan(NEW_REALM);
            assertThat(has(plan, ResourceType.REALM_SETTINGS, NEW_REALM, PlannedChange.Kind.CREATE))
                    .isTrue();
            assertThat(has(plan, ResourceType.CLIENT, "app", PlannedChange.Kind.CREATE))
                    .isTrue();
            assertThat(has(plan, ResourceType.GROUP, "/team", PlannedChange.Kind.CREATE))
                    .isTrue();
        }
        assertThat(realmExists(NEW_REALM)).isFalse();
    }

    @Test
    void applyCreatesTheRealm() {
        try (Connection connection = connect()) {
            ImportReport report = connection.apply(List.of(realmFile(APPLIED_REALM, "https://app.example.org/*")));

            assertThat(has(report.plan(APPLIED_REALM), ResourceType.CLIENT, "app", PlannedChange.Kind.CREATE))
                    .isTrue();
        }
        try (Keycloak admin = admin()) {
            assertThat(admin.realm(APPLIED_REALM).clients().findByClientId("app"))
                    .singleElement()
                    .satisfies(app -> assertThat(app.getRedirectUris()).containsExactly("https://app.example.org/*"));
            assertThat(admin.realm(APPLIED_REALM).groups().groups()).anyMatch(group -> "team".equals(group.getName()));
        }
    }

    @Test
    void aDryRunAfterApplyHasNoChange() {
        List<ImportFile> files = List.of(realmFile(UNCHANGED_REALM, "https://app.example.org/*"));
        try (Connection connection = connect()) {
            connection.apply(files);

            ImportReport report = connection.dryRun(files);

            assertThat(report.plan(UNCHANGED_REALM)).isEmpty();
        }
    }

    @Test
    void aDryRunShowsAnUpdateAndADeletion() {
        try (Connection connection = connect()) {
            connection.apply(List.of(realmFile(CHANGED_REALM, "https://app.example.org/*")));
            try (Keycloak admin = admin()) {
                GroupRepresentation extra = new GroupRepresentation();
                extra.setName("extra");
                admin.realm(CHANGED_REALM).groups().add(extra).close();
            }

            ImportReport report = connection.dryRun(List.of(realmFile(CHANGED_REALM, "https://changed.example.org/*")));

            List<PlannedChange> plan = report.plan(CHANGED_REALM);
            assertThat(has(plan, ResourceType.CLIENT, "app", PlannedChange.Kind.UPDATE))
                    .isTrue();
            assertThat(has(plan, ResourceType.GROUP, "/extra", PlannedChange.Kind.DELETE))
                    .isTrue();
        }
        // the dry run wrote nothing
        try (Keycloak admin = admin()) {
            assertThat(admin.realm(CHANGED_REALM).groups().groups()).anyMatch(group -> "extra".equals(group.getName()));
            assertThat(admin.realm(CHANGED_REALM).clients().findByClientId("app"))
                    .singleElement()
                    .satisfies(app -> assertThat(app.getRedirectUris()).containsExactly("https://app.example.org/*"));
        }
    }

    @Test
    void anInvalidFileFailsWithCode2() {
        try (Connection connection = connect()) {
            ImportFile broken = new ImportFile("target-import-it", "broken.json", "{ \"realm\": ");

            Throwable dryRunFailure = catchThrowable(() -> connection.dryRun(List.of(broken)));
            Throwable applyFailure = catchThrowable(() -> connection.apply(List.of(broken)));

            assertThat(dryRunFailure).isInstanceOf(InvalidImportException.class);
            assertThat(ExitCodeResolver.resolve(dryRunFailure).code()).isEqualTo(2);
            assertThat(applyFailure).isInstanceOf(InvalidImportException.class);
            assertThat(ExitCodeResolver.resolve(applyFailure).code()).isEqualTo(2);
        }
    }

    @Test
    void theTargetContextHasNoRunner() {
        try (Connection connection = connect()) {
            ConfigurableApplicationContext context = ((TargetConnection) connection).context();

            assertThat(context.getBeanNamesForType(ApplicationRunner.class)).isEmpty();
            assertThat(context.getBeanNamesForType(CommandLineRunner.class)).isEmpty();
            // the use case is built for each call, with the options of the call
            assertThat(context.getBeanNamesForType(ImportRealms.class)).isEmpty();
            // no normalization, no other connection
            assertThat(context.getBeanNamesForType(RealmExportParser.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TargetConnectionConfiguration.class))
                    .hasSize(1);
            // the default options of the command-line import
            assertThat(context.getBean(KeycloakImportOptions.class))
                    .isEqualTo(new KeycloakImportOptions(true, false, false));
        }
    }

    private static Connection connect() {
        return new ChildContextKeycloakConnector(mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions())
                .connect(passwordTarget(), ADMIN_PASSWORD);
    }

    private static Target passwordTarget() {
        return new Target(
                "local",
                URL,
                "master",
                "admin-cli",
                GrantType.PASSWORD,
                Optional.of(ADMIN),
                true,
                Duration.ofSeconds(5));
    }

    /** A realm with a client and a group, the client sending its users back to the given URI. */
    private static ImportFile realmFile(final String realm, final String redirectUri) {
        String content = """
                {
                  "enabled": true,
                  "realm": "%s",
                  "groups": [ { "name": "team" } ],
                  "clients": [
                    { "clientId": "app", "publicClient": true, "redirectUris": [ "%s" ] }
                  ]
                }
                """.formatted(realm, redirectUri);
        return new ImportFile("target-import-it", realm + ".json", content);
    }

    private static boolean has(
            final List<PlannedChange> plan, final ResourceType type, final String key, final PlannedChange.Kind kind) {
        return plan.stream()
                .anyMatch(change ->
                        change.type() == type && String.valueOf(change.key()).equals(key) && change.kind() == kind);
    }

    private static boolean realmExists(final String realm) {
        try (Keycloak admin = admin()) {
            return admin.realms().findAll().stream().anyMatch(existing -> realm.equals(existing.getRealm()));
        }
    }

    private static Keycloak admin() {
        return KeycloakBuilder.builder()
                .serverUrl(URL)
                .realm("master")
                .clientId("admin-cli")
                .username(ADMIN)
                .password(ADMIN_PASSWORD)
                .build();
    }
}
