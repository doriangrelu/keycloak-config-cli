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

import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/** The SHELL operation (ADR-008): a session or one command, and never a shell for the other operations. */
class ShellLauncherIT {

    @TempDir
    Path root;

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
            .withUserConfiguration(
                    CommandLineConfiguration.class, ShellCommandConfiguration.class, ShellAdaptersConfiguration.class);

    @Test
    void importModeRegistersAnInactiveShellRunner() {
        this.contexts.withPropertyValues("run.operation=IMPORT").run(context -> {
            ApplicationRunner runner = context.getBean("springShellApplicationRunner", ApplicationRunner.class);

            assertThat(runner).isNotInstanceOf(ShellLauncher.class);
            runner.run(new DefaultApplicationArguments());
        });
    }

    @Test
    void normalizeModeRegistersAnInactiveShellRunner() {
        this.contexts.withPropertyValues("run.operation=NORMALIZE").run(context -> {
            ApplicationRunner runner = context.getBean("springShellApplicationRunner", ApplicationRunner.class);

            assertThat(runner).isNotInstanceOf(ShellLauncher.class);
            runner.run(new DefaultApplicationArguments());
        });
    }

    @Test
    void theOperationIsCaseInsensitive() {
        this.contexts
                .withPropertyValues("run.operation=shell")
                .run(context -> assertThat(context.getBean("springShellApplicationRunner"))
                        .isInstanceOf(ShellLauncher.class));
    }

    @Test
    void shellModeRegistersTheLauncher() {
        this.contexts
                .withPropertyValues("run.operation=SHELL")
                .run(context -> assertThat(context.getBean("springShellApplicationRunner"))
                        .isInstanceOf(ShellLauncher.class));
    }

    @Test
    void scriptModeRunsOneCommandAndExits() {
        this.contexts.withPropertyValues("run.operation=SHELL").run(context -> {
            StringWriter output = new StringWriter();
            ShellLauncher launcher = launcher(context, "help", output);

            launcher.run(new DefaultApplicationArguments());

            assertThat(launcher.getExitCode()).isZero();
            assertThat(output.toString()).contains("help");
        });
    }

    @Test
    void scriptModeUnknownCommandIsInvalidConfiguration() {
        this.contexts.withPropertyValues("run.operation=SHELL").run(context -> {
            ShellLauncher launcher = launcher(context, "nope", new StringWriter());

            launcher.run(new DefaultApplicationArguments());

            assertThat(launcher.getExitCode()).isEqualTo(2);
        });
    }

    @Test
    void anUnknownDisabledRuleStopsTheShell() {
        this.contexts
                .withPropertyValues("run.operation=SHELL", "shell.rules=src/test/resources/rules/unknown-disabled.yaml")
                .run(context -> {
                    StringWriter output = new StringWriter();
                    ShellLauncher launcher = launcher(context, "help", output);

                    launcher.run(new DefaultApplicationArguments());

                    assertThat(launcher.getExitCode()).isEqualTo(2);
                    assertThat(output.toString()).contains("Unknown rule");
                });
    }

    /** What a launch gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    private Outcome launch(
            final ApplicationContext context, final String command, final String realm, final Path script) {
        StringWriter output = new StringWriter();
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(this.root, null, command, realm, script),
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                context.getBean(ShellSession.class),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(org.springframework.shell.core.ShellRunner.class),
                new PrintWriter(output, true));
        launcher.run(new DefaultApplicationArguments());
        return new Outcome(launcher.getExitCode(), output.toString());
    }

    private Path script(final String... lines) throws Exception {
        Path file = this.root.resolve("session.kcc");
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private ApplicationContextRunner shellContexts() {
        return this.contexts.withPropertyValues(
                "run.operation=SHELL", "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    @Test
    void aScriptRunsItsCommandsInOneSession() {
        shellContexts().run(context -> {
            Path file = script(
                    "realm create --folder demo --realm demo --format yaml",
                    "client create --id svc --name S --description d --type service --secret none --declare-type",
                    "role create --name admin --declare-type");

            Outcome outcome = launch(context, null, null, file);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("> realm create --folder demo --realm demo --format yaml");
            assertThat(this.root.resolve("demo/20-clients.yaml")).exists();
            assertThat(this.root.resolve("demo/30-roles.yaml")).exists();
        });
    }

    @Test
    void aFailingCommandStopsTheScript() {
        shellContexts().run(context -> {
            Path file = script(
                    "realm create --folder demo --realm demo --format yaml",
                    "client create --id front --name F --description d --type spa --url https://a"
                            + " --redirect-uris * --web-origins \"-\" --declare-type",
                    "role create --name admin --declare-type");

            Outcome outcome = launch(context, null, null, file);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Stopped at line 2: client create");
            assertThat(this.root.resolve("demo/30-roles.yaml")).doesNotExist();
        });
    }

    @Test
    void commentsAndBlankLinesAreSkipped() {
        shellContexts().run(context -> {
            Path file = script("# a comment", "", "   ", "  help  ");

            Outcome outcome = launch(context, null, null, file);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("> help").doesNotContain("> #");
        });
    }

    @Test
    void aScriptWithoutCommandSaysSo() {
        shellContexts().run(context -> {
            Path file = script("# nothing", "");

            Outcome outcome = launch(context, null, null, file);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("No command in " + file);
        });
    }

    @Test
    void aMissingScriptFails() {
        shellContexts().run(context -> {
            Outcome outcome = launch(context, null, null, this.root.resolve("absent.kcc"));

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("absent.kcc");
        });
    }

    @Test
    void commandAndScriptTogetherFail() {
        shellContexts().run(context -> {
            Outcome outcome = launch(context, "help", null, script("help"));

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("shell.command and shell.script cannot be used together");
        });
    }

    @Test
    void theRealmPropertyOpensTheRealm() {
        shellContexts().run(context -> {
            assertThat(launch(context, "realm create --folder demo --realm demo --format yaml", null, null)
                            .exitCode())
                    .isZero();

            Outcome outcome = launch(context, "client list", "demo", null);

            assertThat(outcome.exitCode()).isZero();
            assertThat(context.getBean(ShellSession.class).openedRealm()).contains("demo");
        });
    }

    @Test
    void anUnknownRealmPropertyFails() {
        shellContexts().run(context -> {
            Outcome outcome = launch(context, "help", "nope", null);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("nope");
        });
    }

    private static ShellLauncher launcher(
            final ApplicationContext context, final String command, final StringWriter output) {
        ShellProperties properties = new ShellProperties(null, null, command, null, null);
        return new ShellLauncher(
                properties,
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                context.getBean(ShellSession.class),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(org.springframework.shell.core.ShellRunner.class),
                new PrintWriter(output, true));
    }
}
