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

import io.github.doriangrelu.keycloak.config.application.definition.NoRealmOpenedException;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/** The realm commands, on the folders of src/test/resources/definition/realms. */
class RealmCommandsIT {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
            .withUserConfiguration(
                    CommandLineConfiguration.class, ShellCommandConfiguration.class, ShellAdaptersConfiguration.class)
            .withPropertyValues("run.operation=SHELL", "shell.root=src/test/resources/definition/realms");

    @Test
    void listOpenShow() {
        this.contexts.run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThat(String.join("\n", shell.sendCommand("realm list").lines()))
                    .contains("split")
                    .contains("broken");
            assertThat(String.join("\n", shell.sendCommand("realm open split").lines()))
                    .contains("Realm 'split' opened (3 clients, 2 roles, 2 groups)");
            assertThat(String.join("\n", shell.sendCommand("realm show").lines()))
                    .contains("frontend")
                    .contains("SPA")
                    .contains("20-clients.yaml:3")
                    .contains("/team/devs");
        });
    }

    @Test
    void aReadOnlyRealmSaysWhichFileToFix() {
        this.contexts.run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThat(String.join("\n", shell.sendCommand("realm open broken").lines()))
                    .contains("00-realm.yaml:2")
                    .contains("read-only");
        });
    }

    /**
     * The session of Spring Shell and the script mode of {@link ShellLauncher} print the message of the cause; the test
     * client of Spring Shell throws the failure instead.
     */
    @Test
    void showWithoutAnOpenedRealmExplainsWhatToDo() {
        this.contexts.run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThatThrownBy(() -> shell.sendCommand("realm show"))
                    .hasRootCauseInstanceOf(NoRealmOpenedException.class)
                    .hasRootCauseMessage("No realm opened: use 'realm open <folder>'");
        });
    }

    @Test
    void openingARealmShowsItsFindings() {
        this.contexts
                .withPropertyValues("shell.root=src/test/resources/definition/findings")
                .run(context -> {
                    ShellTestClient shell = new ShellTestClient(
                            context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

                    assertThat(String.join(
                                    "\n",
                                    shell.sendCommand("realm open wildcard").lines()))
                            .contains("Rule findings:")
                            .contains("  20-clients.yaml:2 open-door")
                            .contains("    [required] client.redirect-uris.no-wildcard: ");
                });
    }

    @Test
    void openingARealmWithoutFindingsShowsNone() {
        this.contexts
                .withPropertyValues("shell.root=src/test/resources/definition/findings")
                .run(context -> {
                    ShellTestClient shell = new ShellTestClient(
                            context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

                    assertThat(String.join(
                                    "\n", shell.sendCommand("realm open clean").lines()))
                            .doesNotContain("Rule findings:")
                            .doesNotContain("Disabled rules:");
                });
    }

    @Test
    void openingARealmShowsTheDisabledRules() {
        this.contexts
                .withPropertyValues("shell.rules=src/test/resources/rules/disable-pkce.yaml")
                .run(context -> {
                    ShellTestClient shell = new ShellTestClient(
                            context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

                    assertThat(String.join(
                                    "\n", shell.sendCommand("realm open split").lines()))
                            .contains("Disabled rules:")
                            .contains("  client.spa.pkce (legacy-portal): Legacy portal without PKCE support.");
                });
    }

    @Test
    void aReadOnlyRealmStillOpensAndShowsWhatCanBeChecked() {
        this.contexts.run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            assertThat(String.join("\n", shell.sendCommand("realm open broken").lines()))
                    .contains("Realm 'broken' opened")
                    .contains("read-only");
        });
    }
}
