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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.shell.test.ShellInputProvider.providerFor;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import jakarta.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
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
import org.springframework.shell.test.ShellTestClient;
import org.testcontainers.containers.GenericContainer;

/** {@code realm dry-run} and {@code realm import} through the real launcher, against a real Keycloak. */
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class RealmImportKeycloakIT {

    private static final GenericContainer<?> KEYCLOAK = AbstractImportIT.KEYCLOAK_CONTAINER;
    private static final String URL = System.getProperty("keycloak.url");
    private static final String ADMIN = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN");
    private static final String ADMIN_PASSWORD = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN_PASSWORD");

    private static final String REALM = "shell-import-it";
    private static final String REFUSED_REALM = "shell-import-refused-it";
    private static final String SECRET = "m4rk3r-shop-s3cr3t-kc-never-printed";
    private static final String TEMPORARY_ADMIN = "shell-import-temporary-admin";
    private static final String TEMPORARY_PASSWORD = "temporary-admin-password-31";

    @TempDir
    Path root;

    @BeforeAll
    @AfterAll
    static void removeTheRealmsAndTheUser() {
        try (Keycloak admin = admin()) {
            admin.realms().findAll().stream()
                    .filter(realm -> List.of(REALM, REFUSED_REALM).contains(realm.getRealm()))
                    .forEach(realm -> admin.realm(realm.getRealm()).remove());
            admin.realm("master")
                    .users()
                    .searchByUsername(TEMPORARY_ADMIN, true)
                    .forEach(user ->
                            admin.realm("master").users().delete(user.getId()).close());
        }
    }

    private ApplicationContextRunner shellContexts(final Map<String, String> environment) throws Exception {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n"
                        + "  local:\n    url: " + URL + "\n    user: " + ADMIN + "\n"
                        + "  temporary:\n    url: " + URL + "\n    user: " + TEMPORARY_ADMIN + "\n",
                StandardCharsets.UTF_8);
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

    /** A realm of the shell: its settings, and a confidential client whose secret is a variable. */
    private void writeRealm(final String realm) throws Exception {
        Path folder = Files.createDirectories(this.root.resolve(realm));
        Files.writeString(
                folder.resolve("00-realm.yaml"),
                "realm: " + realm + "\nenabled: true\ndisplayName: \"{{title:Shop realm}}\"\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                folder.resolve("20-clients.yaml"),
                "realm: " + realm + "\n"
                        + "clients:\n"
                        + "  - clientId: shop\n"
                        + "    enabled: true\n"
                        + "    publicClient: false\n"
                        + "    secret: \"{{shopSecret}}\"\n"
                        + "    redirectUris:\n"
                        + "      - \"https://shop.example.org/*\"\n",
                StandardCharsets.UTF_8);
    }

    /** What a launch gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    private Outcome script(final ApplicationContext context, final String realm, final String... lines)
            throws Exception {
        Path file = this.root.resolve("session.kcc");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return launch(context, new ShellProperties(this.root, null, null, realm, file, null));
    }

    private static Outcome launch(final ApplicationContext context, final ShellProperties properties) {
        StringWriter output = new StringWriter();
        ShellLauncher launcher = new ShellLauncher(
                properties,
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

    @Test
    void dryRunThenImportThenNoChange() throws Exception {
        writeRealm(REALM);
        shellContexts(Map.of("KCC_LOCAL_PASSWORD", ADMIN_PASSWORD)).run(context -> {
            Outcome dryRun = script(context, REALM, "connect local", "realm dry-run --var shopSecret=" + SECRET);

            assertThat(dryRun.exitCode()).as(dryRun.output()).isEqualTo(10);
            assertThat(dryRun.output())
                    .contains("Dry run of '" + REALM + "' on local (" + URL + ")")
                    .contains("+ Client 'shop'")
                    .doesNotContain(SECRET);
            assertThat(realmExists(REALM)).isFalse();

            Outcome importation = script(
                    context,
                    REALM,
                    "connect local",
                    "realm import --apply --var shopSecret=" + SECRET,
                    "realm dry-run");

            assertThat(importation.exitCode()).as(importation.output()).isZero();
            assertThat(importation.output())
                    .contains("Realm '" + REALM + "' imported on local: ")
                    .contains("Dry run: no change pending.")
                    .doesNotContain(SECRET);
        });
        try (Keycloak admin = admin()) {
            String id = admin.realm(REALM)
                    .clients()
                    .findByClientId("shop")
                    .getFirst()
                    .getId();
            assertThat(admin.realm(REALM).clients().get(id).getSecret().getValue())
                    .isEqualTo(SECRET);
            assertThat(admin.realm(REALM).toRepresentation().getDisplayName()).isEqualTo("Shop realm");
        }
    }

    /**
     * The account of the target is removed from Keycloak once the shell is connected: Keycloak refuses the import, and
     * nothing is written.
     */
    @Test
    void aRefusedOperationDuringImportEndsWithCode4() throws Exception {
        writeRealm(REFUSED_REALM);
        createTemporaryAdmin();
        shellContexts(Map.of("KCC_TEMPORARY_PASSWORD", TEMPORARY_PASSWORD)).run(context -> {
            ShellSession session = context.getBean(ShellSession.class);
            ConnectionCommands connections = context.getBean(ConnectionCommands.class);
            connections.connect("temporary", new ConsoleQuestions(new NoInput(), new PrintWriter(new StringWriter())));
            session.open(REFUSED_REALM);
            deleteTemporaryAdmin();

            Outcome outcome = launch(
                    context,
                    new ShellProperties(
                            this.root, null, "realm import --apply --var shopSecret=" + SECRET, null, null, null));

            assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(4);
            assertThat(outcome.output())
                    .contains("refused an operation of the import on target 'temporary'")
                    .doesNotContain(SECRET)
                    .doesNotContain(TEMPORARY_PASSWORD);
        });
        assertThat(realmExists(REFUSED_REALM)).isFalse();
    }

    /**
     * A value that breaks the file once rendered (an unquoted placeholder): the parser of the import would quote it.
     * The shell names the file and the place, never the content, in a script and in a session.
     */
    @Test
    void anInvalidFileAfterRenderingNeverShowsTheValue() throws Exception {
        String secret = "*s3cr3t-marker-breaking-the-file";
        Path yaml = Files.createDirectories(this.root.resolve("broken-yaml"));
        Files.writeString(
                yaml.resolve("00-realm.yaml"),
                "realm: broken-yaml\nenabled: true\nclients:\n  - clientId: shop\n    secret: {{shopSecret}}\n",
                StandardCharsets.UTF_8);
        Path json = Files.createDirectories(this.root.resolve("broken-json"));
        Files.writeString(
                json.resolve("00-realm.json"),
                "{\"realm\": \"broken-json\",\n \"clients\": [{\"clientId\": \"shop\", \"secret\": {{shopSecret}}}]}\n",
                StandardCharsets.UTF_8);
        shellContexts(Map.of("KCC_LOCAL_PASSWORD", ADMIN_PASSWORD)).run(context -> {
            for (String realm : List.of("broken-yaml", "broken-json")) {
                Outcome outcome = script(
                        context,
                        realm,
                        "connect local",
                        "realm dry-run --var shopSecret=" + secret,
                        "realm import --apply");

                assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(2);
                assertThat(outcome.output())
                        .contains("Unable to parse file '")
                        .contains(" at line ")
                        .doesNotContain("s3cr3t-marker");

                ShellTestClient shell = new ShellTestClient(
                        context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
                shell.sendCommand("realm open " + realm);
                shell.sendCommand("connect local");
                Throwable failure = catchThrowable(() -> shell.sendCommand(
                        providerFor("realm import --apply").withPassword(secret).build()));
                assertThat(failure).isNotNull();
                for (Throwable current = failure; current != null; current = current.getCause()) {
                    assertThat(String.valueOf(current.getMessage())).doesNotContain("s3cr3t-marker");
                    for (Throwable suppressed : current.getSuppressed()) {
                        assertThat(String.valueOf(suppressed.getMessage())).doesNotContain("s3cr3t-marker");
                    }
                }
            }
        });
        assertThat(realmExists("broken-yaml")).isFalse();
        assertThat(realmExists("broken-json")).isFalse();
    }

    /** Answers nothing, as in a script. */
    private static final class NoInput implements org.springframework.shell.core.InputReader {}

    private static void createTemporaryAdmin() {
        try (Keycloak admin = admin()) {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(TEMPORARY_ADMIN);
            user.setEnabled(true);
            CredentialRepresentation password = new CredentialRepresentation();
            password.setType(CredentialRepresentation.PASSWORD);
            password.setValue(TEMPORARY_PASSWORD);
            password.setTemporary(false);
            user.setCredentials(List.of(password));
            String id;
            try (Response created = admin.realm("master").users().create(user)) {
                id = CreatedResponseUtil.getCreatedId(created);
            }
            admin.realm("master")
                    .users()
                    .get(id)
                    .roles()
                    .realmLevel()
                    .add(List.of(admin.realm("master").roles().get("admin").toRepresentation()));
        }
    }

    private static void deleteTemporaryAdmin() {
        try (Keycloak admin = admin()) {
            admin.realm("master")
                    .users()
                    .searchByUsername(TEMPORARY_ADMIN, true)
                    .forEach(user ->
                            admin.realm("master").users().delete(user.getId()).close());
        }
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
