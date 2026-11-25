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

/** The role commands, on a realm created in a temporary root. */
class RoleCommandsIT {

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

    private String roles() throws Exception {
        return Files.readString(this.root.resolve("demo/30-roles.yaml"), StandardCharsets.UTF_8);
    }

    private static void shop(final ShellTestClient shell) throws Exception {
        run(shell, "client create --id shop --name Shop --description d --type api --secret none --declare-type");
    }

    @Test
    void aClientRoleIsCreatedByOptions() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            shop(shell);

            String output = run(shell, "role create --client shop --name buyer --description Buys --declare-type");

            assertThat(output).contains("Role 'buyer' written to demo/30-roles.yaml");
            assertThat(roles()).containsSubsequence("client:", "shop", "buyer");
        });
    }

    @Test
    void roleListShowsRealmRolesFirst() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            assertThat(run(shell, "role list")).isEqualTo("No role");
            shop(shell);
            run(shell, "role create --client shop --name buyer --description d --declare-type");
            run(shell, "role create --name zeta --description d --declare-type");
            run(shell, "role create --name alpha --description d");

            assertThat(run(shell, "role list"))
                    .matches("alpha  realm  30-roles\\.yaml:\\d+\\nzeta  realm  30-roles\\.yaml:\\d+"
                            + "\\nbuyer  client shop  30-roles\\.yaml:\\d+");
        });
    }

    @Test
    void editingChangesOnlyTheDescription() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            run(shell, "role create --name admin --description before --declare-type");

            String output = run(shell, "role edit admin --description after");

            assertThat(output).contains("Role 'admin' written to demo/30-roles.yaml");
            assertThat(roles()).contains("admin").contains("after").doesNotContain("before");
        });
    }

    @Test
    void aNameGivenToEditIsRefused() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            run(shell, "role create --name admin --description d --declare-type");

            assertThatThrownBy(() -> run(shell, "role edit admin --name other"))
                    .hasRootCauseInstanceOf(ApplicationException.class)
                    .hasRootCauseMessage(
                            "A role is not renamed by the shell: create a new role and update the groups.");
        });
    }

    @Test
    void aBuiltInClientIsRefused() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            assertThatThrownBy(() -> run(shell, "role create --client realm-management --name x --declare-type"))
                    .hasRootCauseInstanceOf(ApplicationException.class);
            assertThat(this.root.resolve("demo/30-roles.yaml")).doesNotExist();
        });
    }
}
