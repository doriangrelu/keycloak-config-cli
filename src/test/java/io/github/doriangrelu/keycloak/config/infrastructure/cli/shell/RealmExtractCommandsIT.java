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

import io.github.doriangrelu.keycloak.config.application.definition.ClientSecrets;
import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.ExtractedRealm;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RealmNotFoundOnTargetException;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/**
 * {@code realm extract} through the real launcher and through the interactive shell, on the real definition files,
 * with a double of the connection to Keycloak (see {@code RealmExtractKeycloakIT} for a real one).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class RealmExtractCommandsIT {

    private static final String SECRET = "m4rk3r-shop-s3cr3t-never-printed";
    private static final String DOCUMENT = "{\"realm\":\"shop\",\"enabled\":true,"
            + "\"clients\":[{\"clientId\":\"shop\",\"publicClient\":false,\"secret\":\"{{shopSecret}}\"}],"
            + "\"identityProviders\":[{\"alias\":\"google\",\"providerId\":\"google\","
            + "\"config\":{\"clientSecret\":\"{{googleClientSecret}}\"}}],"
            + "\"smtpServer\":{\"host\":\"smtp.example.org\",\"password\":\"{{smtpPassword}}\"}}";
    private static final String USERS =
            "{\"realm\":\"shop\",\"enabled\":true," + "\"users\":[{\"username\":\"alice\",\"enabled\":true}]}";

    @TempDir
    Path root;

    private final Map<String, String> environment = new HashMap<>();
    /** The calls the double was given. */
    private final List<String> calls = new ArrayList<>();

    private final List<List<ImportFile>> dryRuns = new ArrayList<>();
    private final KeycloakConnector connector = (target, secret) -> new FakeConnection(target);

    @BeforeEach
    void writeARealmAndTheTargets() throws IOException {
        Path demo = Files.createDirectories(this.root.resolve("demo"));
        Files.writeString(demo.resolve("00-realm.yaml"), "realm: demo\nenabled: true\n", StandardCharsets.UTF_8);
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n" + "  local:\n" + "    url: http://local.test:8080\n" + "    user: admin\n",
                StandardCharsets.UTF_8);
        this.environment.put("KCC_LOCAL_PASSWORD", "local-password");
    }

    private ApplicationContextRunner contexts() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
                .withUserConfiguration(
                        CommandLineConfiguration.class,
                        ShellCommandConfiguration.class,
                        ShellAdaptersConfiguration.class)
                .withBean(EnvironmentVariables.class, () -> this.environment::get)
                .withBean(
                        "fakeKeycloakConnector",
                        KeycloakConnector.class,
                        () -> this.connector,
                        definition -> definition.setPrimary(true))
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** What a launch gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    private Outcome script(final ApplicationContext context, final String... lines) throws IOException {
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

    private List<String> filesOf(final String folder) throws IOException {
        try (Stream<Path> files = Files.list(this.root.resolve(folder))) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private String contentOf(final String folder) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String file : filesOf(folder)) {
            content.append(Files.readString(this.root.resolve(folder).resolve(file), StandardCharsets.UTF_8));
        }
        return content.toString();
    }

    @Test
    void extractWritesOpensAndListsTheVariables() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract shop");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output())
                    .contains("Realm 'shop' extracted to shop/: 00-realm.yaml, 20-clients.yaml")
                    .contains("Variables to give at import: googleClientSecret, smtpPassword")
                    .contains("(client secrets read from local and kept for this session)")
                    .contains("Default realm role 'offline_access' was deleted in Keycloak: an import may bring"
                            + " it back")
                    .contains("Realm 'shop' opened (1 clients, 0 roles, 0 groups)")
                    .doesNotContain("users from a directory");
            assertThat(filesOf("shop")).containsExactly("00-realm.yaml", "20-clients.yaml");
            assertThat(context.getBean(ShellSession.class).openedRealm()).contains("shop");
            assertThat(this.calls).containsExactly("extract shop false", "secrets shop");
        });
    }

    @Test
    void theOutputOrderFollowsTheConstraints() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract shop --folder shop-copy");

            String output = outcome.output();
            int extracted = output.indexOf("Realm 'shop' extracted to shop-copy/");
            int variables = output.indexOf("Variables to give at import");
            int kept = output.indexOf("(client secrets read from local");
            int warning = output.indexOf("Default realm role");
            int opened = output.indexOf("Realm 'shop-copy' opened");
            assertThat(extracted).isPositive();
            assertThat(List.of(extracted, variables, kept, warning, opened)).isSorted();
        });
    }

    @Test
    void notConnectedFailsWithCode2() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "realm extract shop");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Not connected: use 'connect <target>'");
            assertThat(this.root.resolve("shop")).doesNotExist();
        });
    }

    @Test
    void anExistingFolderFailsWithCode2() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract shop --folder Demo");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Realm folder 'Demo' already exists");
            assertThat(this.calls).isEmpty();
            assertThat(filesOf("demo")).containsExactly("00-realm.yaml");
        });
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract shop --folder ../outside");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(this.calls).isEmpty();
        });
    }

    @Test
    void anUnknownRealmFailsWithCode4AndWritesNothing() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract absent");

            assertThat(outcome.exitCode()).isEqualTo(4);
            assertThat(outcome.output()).contains("Realm 'absent' not found on local");
            assertThat(this.root.resolve("absent")).doesNotExist();
        });
    }

    @Test
    void jsonFormat() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract shop --format json");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output()).contains("Realm 'shop' extracted to shop/: 00-realm.json, 20-clients.json");
            assertThat(filesOf("shop")).containsExactly("00-realm.json", "20-clients.json");
        });
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract shop --folder other --format xml");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(this.root.resolve("other")).doesNotExist();
        });
    }

    @Test
    void withUsers() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract users --with-users");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(this.calls).first().isEqualTo("extract users true");
            assertThat(outcome.output())
                    .contains("Realm 'users' extracted to users/: 00-realm.yaml")
                    .contains("3 users from a directory or service accounts not extracted")
                    .doesNotContain("Variables to give at import")
                    .doesNotContain("client secrets read from");
            assertThat(contentOf("users")).contains("username: alice");
        });
    }

    @Test
    void theClientSecretsAreKeptForTheNextDryRun() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "connect local",
                    "realm extract shop",
                    "realm dry-run --var googleClientSecret=google --var smtpPassword=smtp");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output()).contains("Dry run: no change pending.").doesNotContain("shopSecret'");
            assertThat(this.dryRuns).hasSize(1);
            String sent =
                    this.dryRuns.getFirst().stream().map(ImportFile::content).reduce("", String::concat);
            assertThat(sent).contains(SECRET);
        });
    }

    @Test
    void theClientSecretsAreKeptInAnInteractiveSession() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
            shell.sendCommand("connect local");

            String output = String.join(
                    "\n", shell.sendCommand("realm extract shop --format json").lines());

            assertThat(output)
                    .contains("Realm 'shop' extracted to shop/: 00-realm.json, 20-clients.json")
                    .contains("Realm 'shop' opened")
                    .doesNotContain(SECRET);
            assertThat(context.getBean(ShellSession.class).variables().get("shopSecret"))
                    .contains(SECRET);
        });
    }

    @Test
    void unreadableClientSecretsAreToBeGivenAtImport() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", "realm extract secretless");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output())
                    .contains("Realm 'secretless' extracted to secretless/")
                    .contains("Variables to give at import: googleClientSecret, shopSecret, smtpPassword")
                    .contains("The client secrets could not be read from local: give them at import")
                    .contains("Realm 'secretless' opened")
                    .doesNotContain("kept for this session")
                    .doesNotContain(SECRET);
        });
    }

    @Test
    void noSecretValueInTheOutput() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "connect local",
                    "realm extract shop",
                    "variables list",
                    "realm dry-run --var googleClientSecret=google --var smtpPassword=smtp");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output()).doesNotContain(SECRET);
            assertThat(contentOf("shop")).doesNotContain(SECRET).contains("{{shopSecret}}");
        });
    }

    /** Extracts the realms of the test, and records the calls. */
    private final class FakeConnection implements Connection {

        private final Target target;

        FakeConnection(final Target target) {
            this.target = target;
        }

        @Override
        public Target target() {
            return this.target;
        }

        @Override
        public String keycloakVersion() {
            return "26.5.3";
        }

        @Override
        public ImportReport dryRun(final List<ImportFile> files) {
            RealmExtractCommandsIT.this.calls.add("dry-run");
            RealmExtractCommandsIT.this.dryRuns.add(files);
            return new ImportReport(Map.of("shop", List.of()));
        }

        @Override
        public ImportReport apply(final List<ImportFile> files) {
            throw new UnsupportedOperationException("no import in this test");
        }

        @Override
        public ExtractedRealm extract(final String realm, final boolean withUsers) {
            RealmExtractCommandsIT.this.calls.add("extract " + realm + " " + withUsers);
            return switch (realm) {
                case "shop", "secretless" ->
                    new ExtractedRealm(
                            realm,
                            DOCUMENT.replace("\"realm\":\"shop\"", "\"realm\":\"" + realm + "\""),
                            List.of("googleClientSecret", "shopSecret", "smtpPassword"),
                            List.of("Default realm role 'offline_access' was deleted in Keycloak: an import may"
                                    + " bring it back"),
                            0);
                case "users" ->
                    new ExtractedRealm(
                            realm, USERS.replace("\"realm\":\"shop\"", "\"realm\":\"users\""), List.of(), List.of(), 3);
                default -> throw new RealmNotFoundOnTargetException(realm, this.target);
            };
        }

        @Override
        public ClientSecrets clientSecrets(final String realm) {
            RealmExtractCommandsIT.this.calls.add("secrets " + realm);
            return switch (realm) {
                case "shop" -> ClientSecrets.of(Map.of("shopSecret", SECRET));
                case "users" -> ClientSecrets.of(Map.of());
                default -> throw new IllegalStateException("cannot read " + SECRET);
            };
        }

        @Override
        public void close() {}
    }
}
