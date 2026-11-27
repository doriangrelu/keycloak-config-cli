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

package io.github.doriangrelu.keycloak.config.infrastructure.cli.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.testcontainers.containers.GenericContainer;

/** {@code realm extract} then {@code realm dry-run} through the real launcher, against a real Keycloak (ADR-010). */
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class RealmExtractKeycloakIT {

    private static final GenericContainer<?> KEYCLOAK = AbstractImportIT.KEYCLOAK_CONTAINER;
    private static final String URL = System.getProperty("keycloak.url");
    private static final String ADMIN = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN");
    private static final String ADMIN_PASSWORD = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN_PASSWORD");

    private static final String REALM = "shell-extract-it";
    private static final String SHOP_SECRET = "shop-secret-value-kc-5e1b7d";
    private static final String GOOGLE_SECRET = "google-secret-value-kc-8a3c2f";
    private static final String SMTP_PASSWORD = "smtp-password-value-kc-2d9e4a";
    private static final String USER_PASSWORD = "alice-password-value-kc-6f0b1c";
    private static final String MASK = "**********";

    @TempDir
    Path root;

    @BeforeAll
    static void createTheRealm() {
        removeTheRealm();
        try (Keycloak admin = admin()) {
            RealmRepresentation realm = new RealmRepresentation();
            realm.setRealm(REALM);
            realm.setEnabled(true);
            realm.setLoginWithEmailAllowed(false);
            realm.setDisplayName("Extracted shop");
            realm.setSmtpServer(Map.of(
                    "host", "smtp.example.org",
                    "port", "587",
                    "from", "noreply@example.org",
                    "auth", "true",
                    "user", "mailer",
                    "password", SMTP_PASSWORD));
            admin.realms().create(realm);
            RealmResource created = admin.realm(REALM);

            ClientRepresentation shop = new ClientRepresentation();
            shop.setClientId("shop");
            shop.setPublicClient(false);
            shop.setSecret(SHOP_SECRET);
            shop.setRedirectUris(List.of("https://shop.example.org/*"));
            created.clients().create(shop).close();
            ClientRepresentation web = new ClientRepresentation();
            web.setClientId("web");
            web.setPublicClient(true);
            web.setRedirectUris(List.of("https://web.example.org/*"));
            created.clients().create(web).close();

            created.roles().create(role("manager"));
            String shopId = created.clients().findByClientId("shop").getFirst().getId();
            created.clients().get(shopId).roles().create(role("viewer"));

            GroupRepresentation staff = new GroupRepresentation();
            staff.setName("staff");
            created.groups().add(staff).close();
            String staffId = created.getGroupByPath("/staff").getId();
            GroupRepresentation admins = new GroupRepresentation();
            admins.setName("admins");
            created.groups().group(staffId).subGroup(admins).close();
            created.groups()
                    .group(staffId)
                    .roles()
                    .realmLevel()
                    .add(List.of(created.roles().get("manager").toRepresentation()));
            String adminsId = created.getGroupByPath("/staff/admins").getId();
            created.groups()
                    .group(adminsId)
                    .roles()
                    .clientLevel(shopId)
                    .add(List.of(
                            created.clients().get(shopId).roles().get("viewer").toRepresentation()));

            IdentityProviderRepresentation google = new IdentityProviderRepresentation();
            google.setAlias("google");
            google.setProviderId("google");
            google.setEnabled(true);
            // as the admin console creates it
            google.setFirstBrokerLoginFlowAlias("first broker login");
            google.setConfig(Map.of("clientId", "google-client", "clientSecret", GOOGLE_SECRET));
            created.identityProviders().create(google).close();

            UserRepresentation alice = new UserRepresentation();
            alice.setUsername("alice");
            alice.setEnabled(true);
            alice.setEmail("alice@example.org");
            alice.setFirstName("Alice");
            alice.setLastName("Martin");
            CredentialRepresentation password = new CredentialRepresentation();
            password.setType(CredentialRepresentation.PASSWORD);
            password.setValue(USER_PASSWORD);
            password.setTemporary(false);
            alice.setCredentials(List.of(password));
            created.users().create(alice).close();
        }
    }

    /** The realm lives in the container shared by the other ITs: it is removed. */
    @AfterAll
    static void removeTheRealm() {
        try (Keycloak admin = admin()) {
            admin.realms().findAll().stream()
                    .filter(realm -> REALM.equals(realm.getRealm()))
                    .forEach(realm -> admin.realm(realm.getRealm()).remove());
        }
    }

    private ApplicationContextRunner shellContexts() throws Exception {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n  local:\n    url: " + URL + "\n    user: " + ADMIN + "\n",
                StandardCharsets.UTF_8);
        Map<String, String> environment = Map.of("KCC_LOCAL_PASSWORD", ADMIN_PASSWORD);
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
                .withUserConfiguration(
                        CommandLineConfiguration.class,
                        ShellCommandConfiguration.class,
                        ShellAdaptersConfiguration.class)
                .withBean(EnvironmentVariables.class, () -> environment::get)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** What a launch gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    private Outcome script(final ApplicationContext context, final String... lines) throws Exception {
        Path file = this.root.resolve("session.kcc");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        StringWriter output = new StringWriter();
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(this.root, null, null, null, file, null),
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                context.getBean(ShellSession.class),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(ShellRunner.class),
                new PrintWriter(output, true),
                context.getBean(ConnectionCommands.class));
        launcher.run(new DefaultApplicationArguments());
        return new Outcome(launcher.getExitCode(), output.toString());
    }

    /** ADR-010: on the same target, right after the extraction, the dry run of the extracted realm changes nothing. */
    @Test
    void extractThenDryRunHasNoChange() throws Exception {
        shellContexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "connect local",
                    "realm extract " + REALM,
                    "realm dry-run --var googleClientSecret=" + GOOGLE_SECRET + " --var smtpPassword=" + SMTP_PASSWORD);

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output())
                    .contains("Realm '" + REALM + "' extracted to " + REALM + "/: 00-realm.yaml, 20-clients.yaml,"
                            + " 30-roles.yaml, 40-groups.yaml")
                    .contains("Variables to give at import: googleClientSecret, smtpPassword")
                    .contains("(client secrets read from local and kept for this session)")
                    .contains("Realm '" + REALM + "' opened")
                    .contains("Dry run: no change pending.")
                    .doesNotContain(SHOP_SECRET, GOOGLE_SECRET, SMTP_PASSWORD, MASK);
        });
    }

    /** Review point 1: no secret, and not the mask of Keycloak, in any file of the folder. */
    @Test
    void noSecretInTheWrittenFiles() throws Exception {
        shellContexts().run(context -> {
            Outcome outcome =
                    script(context, "connect local", "realm extract " + REALM + " --folder with-users --with-users");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output()).doesNotContain(SHOP_SECRET, GOOGLE_SECRET, SMTP_PASSWORD, USER_PASSWORD);
            StringBuilder content = new StringBuilder();
            try (Stream<Path> files = Files.list(this.root.resolve("with-users"))) {
                for (Path file : files.toList()) {
                    content.append(Files.readString(file, StandardCharsets.UTF_8));
                }
            }
            assertThat(content.toString())
                    .contains("{{shopSecret}}", "{{googleClientSecret}}", "{{smtpPassword}}", "alice")
                    .doesNotContain(SHOP_SECRET, GOOGLE_SECRET, SMTP_PASSWORD, USER_PASSWORD, MASK);
        });
    }

    private static RoleRepresentation role(final String name) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        return role;
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
