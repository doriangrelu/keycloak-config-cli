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

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.application.definition.UnknownAssignableRoleException;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/** The group commands, on a realm created in a temporary root. */
class GroupCommandsIT {

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

    private static ShellTestClient shellOf(final AssertableApplicationContext context) throws Exception {
        ShellTestClient shell =
                new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
        shell.sendCommand("realm create --folder demo --realm demo --format yaml");
        return shell;
    }

    private static String run(final ShellTestClient shell, final String command) throws Exception {
        return String.join("\n", shell.sendCommand(command).lines());
    }

    private String groups() throws Exception {
        return Files.readString(this.root.resolve("demo/40-groups.yaml"), StandardCharsets.UTF_8);
    }

    private static void top(final ShellTestClient shell) throws Exception {
        run(shell, "role create --name admin --description d --declare-type");
        run(shell, "group create --name top --realm-roles none --client-roles none --declare-type");
    }

    @Test
    void aSubgroupIsCreatedByOptions() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            top(shell);

            String output = run(shell, "group create --name sub --parent /top --realm-roles admin --client-roles none");

            assertThat(output).contains("Group '/top/sub' written to demo/40-groups.yaml");
            assertThat(groups()).contains("top").contains("sub").contains("admin");
        });
    }

    @Test
    void groupListShowsTheTree() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            assertThat(run(shell, "group list")).isEqualTo("No group");
            run(shell, "group create --name alpha --realm-roles none --client-roles none --declare-type");
            top(shell);
            run(shell, "group create --name sub --parent /top --realm-roles admin --client-roles none");

            assertThat(run(shell, "group list"))
                    .matches("alpha  40-groups\\.yaml:\\d+\\ntop  40-groups\\.yaml:\\d+"
                            + "\\n  sub \\[realm: admin\\]  40-groups\\.yaml:\\d+");
        });
    }

    @Test
    void editingReplacesTheRealmRoles() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            top(shell);
            run(shell, "role create --name viewer --description d");

            String output = run(shell, "group edit /top --realm-roles viewer");

            assertThat(output).contains("Group '/top' written to demo/40-groups.yaml");
            assertThat(groups()).contains("viewer").doesNotContain("admin");
        });
    }

    @Test
    void aParentGivenToEditIsRefused() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            top(shell);

            assertThatThrownBy(() -> run(shell, "group edit /top --parent /other"))
                    .hasRootCauseInstanceOf(ApplicationException.class)
                    .hasRootCauseMessage("A group is not renamed or moved by the shell: its members would lose it at"
                            + " the next import.");
        });
    }

    @Test
    void anUnknownRoleIsRefused() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            assertThatThrownBy(() -> run(
                            shell, "group create --name top --realm-roles ghost --client-roles none --declare-type"))
                    .hasRootCauseInstanceOf(UnknownAssignableRoleException.class);
            assertThat(this.root.resolve("demo/40-groups.yaml")).doesNotExist();
        });
    }
}
