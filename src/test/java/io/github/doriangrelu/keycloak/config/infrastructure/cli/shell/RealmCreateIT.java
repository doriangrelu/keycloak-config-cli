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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.shell.test.ShellInputProvider.providerFor;

import io.github.doriangrelu.keycloak.config.application.definition.InvalidRealmFolderException;
import io.github.doriangrelu.keycloak.config.application.definition.RealmFolderExistsException;
import io.github.doriangrelu.keycloak.config.domain.definition.InvalidTemplatedValueException;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/** The realm create command, on an empty root. */
class RealmCreateIT {

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

    @Test
    void createsTheRealmFromItsOptionsAndOpensIt() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThat(String.join(
                            "\n",
                            shell.sendCommand("realm create --folder demo --realm \"{{env}}-demo\" --format json")
                                    .lines()))
                    .contains("Realm 'demo' created: demo/00-realm.json");
            assertThat(this.root.resolve("demo/00-realm.json")).exists();
            assertThat(String.join("\n", shell.sendCommand("realm show").lines()))
                    .contains("Clients:");
        });
    }

    @Test
    void asksForWhatTheOptionsDoNotGive() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            shell.sendCommand(
                    providerFor("realm create").withInput("demo2", "", "1").build());

            assertThat(Files.readString(this.root.resolve("demo2/00-realm.yaml"), StandardCharsets.UTF_8))
                    .contains("realm: demo2");
        });
    }

    @Test
    void anInvalidPlaceholderInTheRealmOptionFailsTheCommand() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThatThrownBy(() -> shell.sendCommand("realm create --folder demo --realm \"{{oops\" --format yaml"))
                    .hasRootCauseInstanceOf(InvalidTemplatedValueException.class);
            assertThat(this.root.resolve("demo")).doesNotExist();
        });
    }

    @Test
    void anUnknownFormatFailsTheCommandNamingTheAcceptedOnes() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThatThrownBy(() -> shell.sendCommand("realm create --folder demo --realm demo --format xml"))
                    .hasRootCauseMessage("Unknown format 'xml': use yaml or json")
                    .hasRootCauseInstanceOf(
                            io.github.doriangrelu.keycloak.config.application.common.ApplicationException.class);
        });
    }

    @Test
    void anInvalidFolderFailsTheCommand() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThatThrownBy(() -> shell.sendCommand("realm create --folder ../x --realm x --format yaml"))
                    .hasRootCauseInstanceOf(InvalidRealmFolderException.class);
        });
    }

    @Test
    void anInvalidFolderOptionFailsBeforeTheOtherQuestionsAndWritesNothing() {
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThatThrownBy(() -> shell.sendCommand("realm create --folder \"my realm\""))
                    .hasRootCauseInstanceOf(InvalidRealmFolderException.class);
            assertThat(this.root).isEmptyDirectory();
        });
    }

    @Test
    void anExistingFolderOptionFailsBeforeTheOtherQuestionsAndWritesNothing() throws Exception {
        Files.createDirectory(this.root.resolve("demo"));
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThatThrownBy(() -> shell.sendCommand("realm create --folder DEMO"))
                    .hasRootCauseInstanceOf(RealmFolderExistsException.class);
            assertThat(this.root.resolve("demo")).isEmptyDirectory();
        });
    }

    @Test
    void anInvalidOrExistingFolderAnswerIsAskedAgain() throws Exception {
        Files.createDirectory(this.root.resolve("demo"));
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            var screen = shell.sendCommand(providerFor("realm create")
                    .withInput("my realm", "Demo", "{{x", "demo3", "", "1")
                    .build());

            assertThat(String.join("\n", screen.lines()))
                    .contains("Invalid realm folder 'my realm'")
                    .contains("already exists");
            assertThat(this.root.resolve("demo3/00-realm.yaml")).exists();
        });
    }
}
