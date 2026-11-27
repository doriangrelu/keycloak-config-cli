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
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/**
 * The group commands in script mode ({@code shell.command}): the real reader answers nothing, so every question
 * takes its proposal, and fails only when it has none.
 */
class GroupCommandsScriptIT {

    @TempDir
    Path root;

    private ApplicationContextRunner contexts() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
                .withUserConfiguration(
                        CommandLineConfiguration.class,
                        ShellCommandConfiguration.class,
                        ShellAdaptersConfiguration.class)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** What a script command gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    private static Outcome script(final AssertableApplicationContext context, final String command) {
        StringWriter output = new StringWriter();
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(null, null, command, null, null, null),
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

    private static void realm(final AssertableApplicationContext context) {
        assertThat(script(context, "realm create --folder demo --realm demo --format yaml")
                        .exitCode())
                .isZero();
    }

    @Test
    void aGroupWithoutRolesIsCreated() {
        contexts().run(context -> {
            realm(context);

            Outcome outcome = script(
                    context, "group create --name top --realm-roles none --client-roles none" + " --declare-type");

            assertThat(outcome.exitCode()).isZero();
            assertThat(Files.readString(this.root.resolve("demo/40-groups.yaml"), StandardCharsets.UTF_8))
                    .contains("top");
        });
    }

    @Test
    void theFirstGroupWithoutDeclareTypeFails() {
        contexts().run(context -> {
            realm(context);

            Outcome outcome = script(context, "group create --name top --realm-roles none --client-roles none");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("--declare-type");
            assertThat(this.root.resolve("demo/40-groups.yaml")).doesNotExist();
        });
    }
}
