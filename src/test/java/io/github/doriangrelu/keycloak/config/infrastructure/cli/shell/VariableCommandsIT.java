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

import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/** The variable commands, on the folders of src/test/resources/definition/realms. */
class VariableCommandsIT {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
            .withUserConfiguration(
                    CommandLineConfiguration.class, ShellCommandConfiguration.class, ShellAdaptersConfiguration.class)
            .withPropertyValues("run.operation=SHELL", "shell.root=src/test/resources/definition/realms");

    @Test
    void listsTheVariablesOfTheOpenedRealm() {
        this.contexts.run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));

            shell.sendCommand("realm open split");
            assertThat(String.join("\n", shell.sendCommand("variables list").lines()))
                    .contains("appUrl")
                    .contains("env")
                    .contains("no default");
        });
    }
}
