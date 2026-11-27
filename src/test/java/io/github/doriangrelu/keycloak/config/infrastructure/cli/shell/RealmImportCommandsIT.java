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

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/**
 * {@code realm dry-run} and {@code realm import} through the real launcher and through the interactive shell, on the
 * real definition files, with a double of the connection to Keycloak (see {@code RealmImportKeycloakIT} for a real
 * one).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class RealmImportCommandsIT {

    private static final String SECRET = "m4rk3r-shop-s3cr3t-never-printed";
    private static final String MISSING =
            "Variable 'shopSecret' has no value: give it with --var shopSecret=… or KCC_VAR_shopSecret";

    @TempDir
    Path root;

    private final Map<String, String> environment = new HashMap<>();
    /** The changes each dry run and import of the double answers. */
    private List<PlannedChange> changes = List.of();
    /** The files the double was given: a dry run, then an import, with the target they were sent to. */
    private final List<String> calls = new ArrayList<>();

    private final List<List<ImportFile>> dryRuns = new ArrayList<>();
    private final List<List<ImportFile>> applied = new ArrayList<>();

    private final KeycloakConnector connector = (target, secret) -> new FakeConnection(target);

    @BeforeEach
    void writeTheRealmsAndTheTargets() throws IOException {
        realm("demo");
        realm("shop");
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n"
                        + "  local:\n"
                        + "    url: http://local.test:8080\n"
                        + "    user: admin\n"
                        + "  other:\n"
                        + "    url: http://other.test:8080\n"
                        + "    user: admin\n",
                StandardCharsets.UTF_8);
        this.environment.put("KCC_LOCAL_PASSWORD", "local-password");
        this.environment.put("KCC_OTHER_PASSWORD", "other-password");
    }

    private void realm(final String name) throws IOException {
        Path folder = Files.createDirectories(this.root.resolve(name));
        Files.writeString(
                folder.resolve("00-realm.yaml"),
                "realm: " + name + "\nenabled: true\ndisplayName: \"{{title:Demo}}\"\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                folder.resolve("20-clients.yaml"),
                "realm: " + name + "\nclients:\n  - clientId: shop\n    secret: \"{{shopSecret}}\"\n",
                StandardCharsets.UTF_8);
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

    private Outcome command(
            final ApplicationContext context, final String command, final String realm, final String target) {
        return launch(context, new ShellProperties(this.root, null, command, realm, null, target));
    }

    private Outcome script(final ApplicationContext context, final String realm, final String... lines)
            throws IOException {
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

    private static PlannedChange clientCreated() {
        return new PlannedChange(ResourceType.CLIENT, "shop", PlannedChange.Kind.CREATE, null, null);
    }

    private static PlannedChange bundleUpdated() {
        return new PlannedChange(
                ResourceType.MESSAGE_BUNDLE,
                "fr",
                PlannedChange.Kind.UPDATE,
                new MessageBundle("fr", Map.of("key", "old")),
                new MessageBundle("fr", Map.of("key", SECRET)));
    }

    private String sentContent(final List<ImportFile> files) {
        StringBuilder content = new StringBuilder();
        files.forEach(file -> content.append(file.content()));
        return content.toString();
    }

    @Test
    void aDryRunWithChangesEndsWithCode10() {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            Outcome outcome = command(context, "realm dry-run --var shopSecret=" + SECRET, "demo", "local");

            assertThat(outcome.exitCode()).isEqualTo(10);
            assertThat(outcome.output())
                    .contains("Dry run of 'demo' on local (http://local.test:8080)")
                    .contains("Realm 'demo': 1 change(s)")
                    .contains("  + Client 'shop'")
                    .contains("Dry run: 1 change(s) pending.")
                    .doesNotContain(SECRET);
            assertThat(this.dryRuns).hasSize(1);
            assertThat(this.applied).isEmpty();
        });
    }

    @Test
    void aDryRunWithoutChangeEndsWithCode0() {
        contexts().run(context -> {
            Outcome outcome = command(context, "realm dry-run --var shopSecret=" + SECRET, "demo", "local");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("Dry run: no change pending.");
        });
    }

    @Test
    void aDryRunInAScriptDoesNotStopIt() throws IOException {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            Outcome outcome =
                    script(context, "demo", "connect local", "realm dry-run --var shopSecret=" + SECRET, "realm show");

            assertThat(outcome.exitCode()).isEqualTo(10);
            assertThat(outcome.output())
                    .contains("Dry run: 1 change(s) pending.")
                    .contains("> realm show")
                    .contains("Clients:")
                    .doesNotContain("Stopped at line");
        });
        contexts().run(context -> {
            Outcome outcome = script(
                    context, "demo", "connect local", "realm dry-run --var shopSecret=" + SECRET, "realm open absent");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Stopped at line 3: realm open absent");
        });
    }

    @Test
    void notConnectedFailsWithCode2() {
        contexts().run(context -> {
            Outcome dryRun = command(context, "realm dry-run --var shopSecret=" + SECRET, "demo", null);
            Outcome importation = command(context, "realm import --apply --var shopSecret=" + SECRET, "demo", null);

            assertThat(dryRun.exitCode()).isEqualTo(2);
            assertThat(dryRun.output()).contains("Not connected: use 'connect <target>'");
            assertThat(importation.exitCode()).isEqualTo(2);
            assertThat(importation.output()).contains("Not connected: use 'connect <target>'");
        });
    }

    @Test
    void noRealmOpenedFailsWithCode2() {
        contexts().run(context -> {
            Outcome dryRun = command(context, "realm dry-run", null, "local");
            Outcome importation = command(context, "realm import --apply", null, "local");

            assertThat(dryRun.exitCode()).isEqualTo(2);
            assertThat(dryRun.output()).contains("No realm opened: use 'realm open <folder>'");
            assertThat(importation.exitCode()).isEqualTo(2);
            assertThat(importation.output()).contains("No realm opened: use 'realm open <folder>'");
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void importWithoutApplyInScriptModeWritesNothing() {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            Outcome outcome = command(context, "realm import --var shopSecret=" + SECRET, "demo", "local");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .contains("Import of 'demo' on local (http://local.test:8080)")
                    .contains("  + Client 'shop'")
                    .contains("Apply these 1 change(s) to local?")
                    .doesNotContain("Dry run:");
            assertThat(outcome.output().stripTrailing()).endsWith("Nothing written: run again with --apply");
            assertThat(this.dryRuns).hasSize(1);
            assertThat(this.applied).isEmpty();
        });
    }

    @Test
    void importWithApplyApplies() {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            Outcome outcome = command(context, "realm import --apply --var shopSecret=" + SECRET, "demo", "local");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .contains("  + Client 'shop'")
                    .doesNotContain("Apply these")
                    .contains("Realm 'demo' imported on local: 1 change(s) applied");
            assertThat(this.calls).containsExactly("dry-run local", "apply local");
            // the files of the plan, not a second reading of the folder
            assertThat(this.applied).singleElement().isSameAs(this.dryRuns.getFirst());
            assertThat(sentContent(this.applied.getFirst())).contains("secret: \"" + SECRET + "\"");
        });
    }

    @Test
    void aRefusedConfirmationWritesNothing() {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
            shell.sendCommand("realm open demo");
            shell.sendCommand("connect local");

            List<String> lines = shell.sendCommand(providerFor("realm import --var shopSecret=" + SECRET)
                            .withInput("n")
                            .build())
                    .lines();

            String output = String.join("\n", lines);
            assertThat(output).contains("Apply these 1 change(s) to local? [y/N]");
            assertThat(output.stripTrailing()).endsWith("Nothing written");
            assertThat(this.applied).isEmpty();
        });
    }

    @Test
    void anEmptyPlanSaysNothingToImport() {
        contexts().run(context -> {
            Outcome outcome = command(context, "realm import --apply --var shopSecret=" + SECRET, "demo", "local");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("Nothing to import").doesNotContain("Apply these");
            assertThat(this.applied).isEmpty();
        });
    }

    @Test
    void aMissingVariableInScriptModeFailsWithCode2() {
        contexts().run(context -> {
            Outcome dryRun = command(context, "realm dry-run", "demo", "local");
            Outcome importation = command(context, "realm import --apply", "demo", "local");

            assertThat(dryRun.exitCode()).isEqualTo(2);
            assertThat(dryRun.output()).contains(MISSING).doesNotContain("import.mustache");
            assertThat(importation.exitCode()).isEqualTo(2);
            assertThat(importation.output()).contains(MISSING);
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void anInvalidVarOptionFailsWithCode2() {
        contexts().run(context -> {
            Outcome outcome = command(context, "realm dry-run --var =" + SECRET, "demo", "local");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output())
                    .contains("Invalid --var: expected name=value")
                    .doesNotContain(SECRET);
        });
    }

    @Test
    void aSecretValueNeverAppearsInTheOutput() throws IOException {
        this.changes = List.of(clientCreated(), bundleUpdated());
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "demo",
                    "connect local",
                    "realm dry-run --var shopSecret=" + SECRET,
                    "realm import --apply",
                    "variables list");

            assertThat(outcome.exitCode()).isEqualTo(10);
            assertThat(outcome.output())
                    .contains("~ Message bundle 'fr' (texts)")
                    .doesNotContain(SECRET);
            assertThat(sentContent(this.dryRuns.getFirst())).contains(SECRET);
        });
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
            shell.sendCommand("realm open demo");
            shell.sendCommand("connect local");

            List<String> lines = shell.sendCommand(
                            providerFor("realm dry-run").withPassword(SECRET).build())
                    .lines();

            assertThat(String.join("\n", lines))
                    .contains("Value of 'shopSecret'")
                    .contains("Dry run: 2 change(s) pending.")
                    .doesNotContain(SECRET);
            assertThat(sentContent(this.dryRuns.getLast())).contains(SECRET);
        });
    }

    @Test
    void connectingToAnotherTargetForgetsTheValues() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "demo",
                    "connect local",
                    "realm dry-run --var shopSecret=" + SECRET,
                    "realm dry-run",
                    "connect other",
                    "realm dry-run");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(this.calls).containsExactly("dry-run local", "dry-run local");
            assertThat(outcome.output()).contains(MISSING).contains("Stopped at line 5: realm dry-run");
        });
    }

    @Test
    void openingAnotherRealmForgetsTheValues() throws IOException {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "demo",
                    "connect local",
                    "realm dry-run --var shopSecret=" + SECRET,
                    "realm open shop",
                    "realm dry-run");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(this.calls).containsExactly("dry-run local");
            assertThat(outcome.output()).contains(MISSING).contains("Stopped at line 4: realm dry-run");
        });
    }

    @Test
    void aGivenValueIsKeptForTheNextCommand() throws IOException {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "demo",
                    "connect local",
                    "realm dry-run --var shopSecret=" + SECRET + " --var title=Shop",
                    "realm import --apply");

            assertThat(outcome.exitCode()).isEqualTo(10);
            assertThat(outcome.output()).contains("Realm 'demo' imported on local: 1 change(s) applied");
            assertThat(this.applied).hasSize(1);
            assertThat(sentContent(this.applied.getFirst()))
                    .contains("secret: \"" + SECRET + "\"")
                    .contains("displayName: \"Shop\"");
        });
    }

    @Test
    void anUnbalancedQuoteNeverShowsTheValue() throws IOException {
        String secret = "ab'cd-s3cr3t-marker-unbalanced";
        contexts().run(context -> {
            Outcome inScript = script(context, "demo", "connect local", "realm dry-run --var shopSecret=" + secret);
            Outcome inCommand = command(context, "realm import --apply --var \"shopSecret=" + secret, "demo", "local");

            assertThat(inScript.exitCode()).isEqualTo(2);
            assertThat(inScript.output())
                    .contains("Unbalanced quote (') in input: realm dry-run --var shopSecret=***")
                    .contains("Stopped at line 2: realm dry-run --var shopSecret=***")
                    .doesNotContain("s3cr3t-marker");
            assertThat(inCommand.exitCode()).isEqualTo(2);
            assertThat(inCommand.output()).contains("Unbalanced quote").doesNotContain("s3cr3t-marker");
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void aValueIsReadFromTheEnvironment() {
        this.changes = List.of(clientCreated());
        this.environment.put("KCC_VAR_shopSecret", SECRET);
        contexts().run(context -> {
            Outcome outcome = command(context, "realm import --apply", "demo", "local");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("Realm 'demo' imported on local: 1 change(s) applied");
            assertThat(sentContent(this.applied.getFirst())).contains("secret: \"" + SECRET + "\"");
        });
    }

    @Test
    void aConfirmationAnsweredYesApplies() {
        this.changes = List.of(clientCreated());
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
            shell.sendCommand("realm open demo");
            shell.sendCommand("connect local");

            List<String> lines = shell.sendCommand(providerFor("realm import --var shopSecret=" + SECRET)
                            .withInput("y")
                            .build())
                    .lines();

            String output = String.join("\n", lines);
            assertThat(output)
                    .contains("Apply these 1 change(s) to local? [y/N]")
                    .contains("Realm 'demo' imported on local: 1 change(s) applied")
                    .doesNotContain(SECRET);
            assertThat(this.applied).singleElement().isSameAs(this.dryRuns.getFirst());
        });
    }

    @Test
    void aVarWithoutEqualSignNeverShowsItsArgument() throws IOException {
        String marker = "s3cr3t-marker-without-equal";
        contexts().run(context -> {
            Outcome outcome = script(context, "demo", "connect local", "realm dry-run --var shopSecret:" + marker);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output())
                    .contains("> realm dry-run --var ***")
                    .contains("Invalid --var: expected name=value")
                    .contains("Stopped at line 2: realm dry-run --var ***")
                    .doesNotContain(marker);

            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
            Throwable failure = catchThrowable(() -> shell.sendCommand("realm dry-run --var shopSecret:" + marker));
            assertThat(failure).isNotNull();
            for (Throwable current = failure; current != null; current = current.getCause()) {
                assertThat(String.valueOf(current.getMessage())).doesNotContain(marker);
            }
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void aVariableWithADefaultInOnlyOnePlaceNeedsAValue() throws IOException {
        Path folder = Files.createDirectories(this.root.resolve("mixed"));
        Files.writeString(
                folder.resolve("00-realm.yaml"),
                "realm: mixed\nenabled: true\ndisplayName: \"{{brand:Shop}}\"\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                folder.resolve("20-clients.yaml"),
                "realm: mixed\nclients:\n  - clientId: shop\n    description: \"{{brand}}\"\n",
                StandardCharsets.UTF_8);
        contexts().run(context -> {
            Outcome missing = command(context, "realm dry-run", "mixed", "local");
            Outcome given = command(context, "realm dry-run --var brand=Market", "mixed", "local");

            assertThat(missing.exitCode()).isEqualTo(2);
            assertThat(missing.output())
                    .contains("Variable 'brand' has no value: give it with --var brand=… or KCC_VAR_brand")
                    .doesNotContain("import.mustache");
            assertThat(given.exitCode()).isZero();
            assertThat(sentContent(this.dryRuns.getLast()))
                    .contains("displayName: \"Market\"")
                    .contains("description: \"Market\"");
        });
    }

    @Test
    void theLoggersThatWouldShowAValueStayAtInfo() {
        List<String> loggers = List.of(
                "org.springframework.shell.core.command.DefaultCommandParser",
                "org.springframework.shell.core.command.adapter.MethodInvokerCommandAdapter",
                "io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.RealmDocuments",
                "io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.CloneUtil");
        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        loggers.forEach(name -> logging.setLogLevel(name, LogLevel.TRACE));
        try {
            contexts().run(context -> {
                for (String name : loggers) {
                    assertThat(logging.getLoggerConfiguration(name).getEffectiveLevel())
                            .as(name)
                            .isEqualTo(LogLevel.INFO);
                }
            });
        } finally {
            loggers.forEach(name -> logging.setLogLevel(name, null));
        }
    }

    /** Records the files of each dry run and import, and answers the changes of the test. */
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
            RealmImportCommandsIT.this.calls.add("dry-run " + this.target.name());
            RealmImportCommandsIT.this.dryRuns.add(files);
            return report(files);
        }

        @Override
        public ImportReport apply(final List<ImportFile> files) {
            RealmImportCommandsIT.this.calls.add("apply " + this.target.name());
            RealmImportCommandsIT.this.applied.add(files);
            return report(files);
        }

        private ImportReport report(final List<ImportFile> files) {
            String realm =
                    files.getFirst().content().lines().findFirst().orElseThrow().substring("realm: ".length());
            return new ImportReport(Map.of(realm, RealmImportCommandsIT.this.changes));
        }

        @Override
        public void close() {}
    }
}
