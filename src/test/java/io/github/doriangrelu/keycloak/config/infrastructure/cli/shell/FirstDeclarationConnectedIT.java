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
import io.github.doriangrelu.keycloak.config.application.definition.FirstDeclarationException;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/**
 * The first client, role or group of a realm, connected to a target: the confirmation lists what the next import would
 * delete there, from a dry run of the files with the element on a double of the connection (see
 * {@code FirstDeclarationKeycloakIT} for a real Keycloak).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class FirstDeclarationConnectedIT {

    private static final String GROUP = "group create --name team --realm-roles none --client-roles none";
    private static final String LISTED =
            "At the next import, these groups of Keycloak missing from the files will be deleted on local (2):"
                    + " /legacy, /old.";
    private static final String OFFLINE = "At the next import, all the groups of Keycloak missing from the files will"
            + " be deleted. Run a dry run first, then confirm.";
    private static final String SCRIPT = " In a script, add --declare-type.";
    private static final String APP_ID = "front-app-remembered";

    @TempDir
    Path root;

    private final Map<String, String> environment = new HashMap<>();
    /** The changes each dry run of the double answers. */
    private List<PlannedChange> changes = List.of();
    /** The files of each dry run the double was given. */
    private final List<List<ImportFile>> dryRuns = new ArrayList<>();

    private final KeycloakConnector connector = (target, secret) -> new FakeConnection(target);

    @BeforeEach
    void writeTheTargets() throws IOException {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n  local:\n    url: http://local.test:8080\n    user: admin\n",
                StandardCharsets.UTF_8);
        this.environment.put("KCC_LOCAL_PASSWORD", "local-password");
        this.changes = List.of(
                deleted(ResourceType.GROUP, "/legacy"),
                deleted(ResourceType.CLIENT, "old-app"),
                deleted(ResourceType.GROUP, "/old"),
                new PlannedChange(ResourceType.GROUP, "/team", PlannedChange.Kind.CREATE, null, null));
    }

    private static PlannedChange deleted(final ResourceType type, final Object key) {
        return new PlannedChange(type, key, PlannedChange.Kind.DELETE, null, null);
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

    /** Creates the realm {@code demo}, then runs the lines on it. */
    private Outcome script(final ApplicationContext context, final String... lines) throws IOException {
        if (!Files.exists(this.root.resolve("demo"))) {
            Outcome created = launch(
                    context,
                    new ShellProperties(
                            this.root,
                            null,
                            "realm create --folder demo --realm demo --format yaml",
                            null,
                            null,
                            null));
            assertThat(created.exitCode()).as(created.output()).isZero();
        }
        Path file = this.root.resolve("session.kcc");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return launch(context, new ShellProperties(this.root, null, null, "demo", file, null));
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

    private ShellTestClient connectedShell(final ApplicationContext context) throws Exception {
        ShellTestClient shell =
                new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
        shell.sendCommand("realm create --folder demo --realm demo --format yaml");
        shell.sendCommand("connect local");
        return shell;
    }

    private Path groups() {
        return this.root.resolve("demo/40-groups.yaml");
    }

    private static String sent(final List<ImportFile> files) {
        StringBuilder content = new StringBuilder();
        files.forEach(file -> content.append(file.content()));
        return content.toString();
    }

    @Test
    void theQuestionListsTheDeletions() {
        contexts().run(context -> {
            ShellTestClient shell = connectedShell(context);

            String output = String.join(
                    "\n",
                    shell.sendCommand(providerFor(GROUP).withInput("y").build()).lines());

            assertThat(output).contains(LISTED + " Confirm?").doesNotContain("old-app");
            assertThat(Files.readString(groups(), StandardCharsets.UTF_8)).contains("team");
            assertThat(this.dryRuns).hasSize(1);
            assertThat(sent(this.dryRuns.getFirst())).contains("team");
        });
    }

    @Test
    void aRefusedQuestionWritesNothing() {
        contexts().run(context -> {
            ShellTestClient shell = connectedShell(context);

            Throwable failure = catchThrowable(
                    () -> shell.sendCommand(providerFor(GROUP).withInput("n").build()));

            assertThat(failure).hasRootCauseInstanceOf(FirstDeclarationException.class);
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause.getMessage()).isEqualTo(LISTED + " Confirm?" + SCRIPT);
            assertThat(groups()).doesNotExist();
            assertThat(this.dryRuns).hasSize(1);
        });
    }

    @Test
    void declareTypeShowsTheListAndWrites() {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", GROUP + " --declare-type");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output()).contains(LISTED).doesNotContain("Confirm?");
            assertThat(Files.readString(groups(), StandardCharsets.UTF_8)).contains("team");
            assertThat(this.dryRuns).hasSize(1);
        });
    }

    @Test
    void everyCreateCommandListsTheDeletionsOfItsType() {
        this.changes = List.of(
                deleted(ResourceType.CLIENT, "old-app"),
                deleted(ResourceType.REALM_ROLE, RoleKey.realm("old-role")),
                deleted(ResourceType.CLIENT_ROLE, RoleKey.client("front", "stale")),
                deleted(ResourceType.CLIENT_ROLE, RoleKey.client("other", "elsewhere")),
                deleted(ResourceType.GROUP, "/legacy"));
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "connect local",
                    "client create --id front --type spa --url https://front.example.org --declare-type",
                    "role create --name viewer --description d --declare-type",
                    "role create --client front --name admin --description d --declare-type");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output())
                    .contains("At the next import, these clients of Keycloak missing from the files will be deleted"
                            + " on local (1): old-app.")
                    .contains("At the next import, these realm roles of Keycloak missing from the files will be"
                            + " deleted on local (1): old-role.")
                    .contains("At the next import, these roles of client 'front' of Keycloak missing from the files"
                            + " will be deleted on local (1): stale.");
            assertThat(this.dryRuns).hasSize(3);
        });
    }

    @Test
    void withoutDeclareTypeTheScriptFailsWithTheList() {
        contexts().run(context -> {
            Outcome outcome = script(context, "connect local", GROUP);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains(LISTED + " Confirm?" + SCRIPT);
            assertThat(groups()).doesNotExist();
            assertThat(this.dryRuns).hasSize(1);
        });
    }

    @Test
    void offlineTheWarningIsUnchanged() {
        contexts().run(context -> {
            Outcome refused = script(context, GROUP);
            Outcome declared = script(context, GROUP + " --declare-type");

            assertThat(refused.exitCode()).isEqualTo(2);
            assertThat(refused.output()).contains(OFFLINE + SCRIPT).doesNotContain("dry run on");
            assertThat(declared.exitCode()).as(declared.output()).isZero();
            assertThat(declared.output()).doesNotContain("At the next import");
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void aTypeAlreadyDeclaredDoesNotSimulate() {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    GROUP + " --declare-type",
                    "connect local",
                    "group create --name second --realm-roles none --client-roles none",
                    "group edit /team --realm-roles none --client-roles none");

            assertThat(outcome.exitCode()).as(outcome.output()).isZero();
            assertThat(outcome.output()).doesNotContain("At the next import");
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void aMissingVariableGivesTheGenericWarning() {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "client create --id \"{{appId}}\" --type spa --url https://front.example.org --declare-type",
                    "connect local",
                    GROUP);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output())
                    .contains(OFFLINE + " (dry run on local not possible: variable 'appId' has no value)" + SCRIPT);
            assertThat(groups()).doesNotExist();
            assertThat(this.dryRuns).isEmpty();
        });
    }

    @Test
    void aRememberedValueIsUsed() {
        contexts().run(context -> {
            Outcome outcome = script(
                    context,
                    "client create --id \"{{appId}}\" --type spa --url https://front.example.org --declare-type",
                    "connect local",
                    "realm dry-run --var appId=" + APP_ID,
                    GROUP + " --declare-type");

            assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(10);
            assertThat(outcome.output()).contains(LISTED).doesNotContain("has no value");
            assertThat(this.dryRuns).hasSize(2);
            assertThat(sent(this.dryRuns.getLast())).contains(APP_ID).contains("team");
        });
    }

    /** Records the files of each dry run, and answers the changes of the test for the realm {@code demo}. */
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
            FirstDeclarationConnectedIT.this.dryRuns.add(files);
            return new ImportReport(Map.of("demo", FirstDeclarationConnectedIT.this.changes));
        }

        @Override
        public ImportReport apply(final List<ImportFile> files) {
            throw new AssertionError("nothing is imported");
        }

        @Override
        public void close() {}
    }
}
