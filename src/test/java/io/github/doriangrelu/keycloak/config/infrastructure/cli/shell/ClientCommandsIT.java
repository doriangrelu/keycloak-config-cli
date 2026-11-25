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
import io.github.doriangrelu.keycloak.config.application.definition.HardCodedSecretException;
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

/** The client commands, on a realm created in a temporary root. */
class ClientCommandsIT {

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

    private String clients() throws Exception {
        return Files.readString(this.root.resolve("demo/20-clients.yaml"), StandardCharsets.UTF_8);
    }

    @Test
    void aSpaCreatedByOptionsIsWrittenWithPkce() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            String output = run(
                    shell,
                    "client create --id frontend --name Front --description \"The front\" --type spa"
                            + " --url \"{{appUrl}}\" --declare-type");

            assertThat(output)
                    .contains("Client 'frontend' written to demo/20-clients.yaml")
                    .contains("set by the rules");
            assertThat(clients()).contains("{{appUrl}}/*").contains("pkce.code.challenge.method");
        });
    }

    @Test
    void aHardCodedSecretOptionFails() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            assertThatThrownBy(() -> run(
                            shell,
                            "client create --id shop --name Shop --description d --type web-application"
                                    + " --url https://shop --secret s3cr3t --declare-type"))
                    .hasRootCauseInstanceOf(HardCodedSecretException.class);
            assertThat(this.root.resolve("demo/20-clients.yaml")).doesNotExist();
        });
    }

    @Test
    void theFirstClientWithoutDeclareTypeFailsInScriptMode() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            assertThatThrownBy(() -> run(
                            shell,
                            "client create --id frontend --name Front --description d --type spa --url https://a"))
                    .hasRootCauseInstanceOf(ApplicationException.class);
            assertThat(this.root.resolve("demo/20-clients.yaml")).doesNotExist();
        });
    }

    @Test
    void anUnknownTypeFailsNamingTheAcceptedOnes() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            assertThatThrownBy(() -> run(shell, "client create --id a --type nope --declare-type"))
                    .hasRootCauseInstanceOf(ApplicationException.class)
                    .hasRootCauseMessage("Unknown application type 'nope': use spa, web-application, service or api");
            assertThatThrownBy(() -> run(shell, "client create --id a --type custom --declare-type"))
                    .hasRootCauseInstanceOf(ApplicationException.class)
                    .hasRootCauseMessage("Unknown application type 'custom': use spa, web-application, service or api");
        });
    }

    @Test
    void clientListShowsTypeAndPlace() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            assertThat(run(shell, "client list")).isEqualTo("No client");
            run(shell, "client create --id zeta --name Z --description d --type api --secret none --declare-type");
            run(shell, "client create --id alpha --name A --description d --type spa --url https://a");

            assertThat(run(shell, "client list"))
                    .matches("alpha  spa  20-clients\\.yaml:\\d+\\nzeta  api  20-clients\\.yaml:\\d+");
        });
    }

    @Test
    void editingByOptionsChangesTheDescription() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);
            run(shell, "client create --id svc --name S --description before --type api --secret none --declare-type");

            String output = run(shell, "client edit svc --name S --description after --type api --secret none");

            assertThat(output).contains("Client 'svc' written to demo/20-clients.yaml");
            assertThat(clients()).contains("after").doesNotContain("before");
        });
    }

    @Test
    void aWildcardRedirectUriIsRefusedByTheRules() {
        contexts().run(context -> {
            ShellTestClient shell = shellOf(context);

            assertThatThrownBy(() -> run(
                            shell,
                            "client create --id front --name F --description d --type spa --url https://a"
                                    + " --redirect-uris * --web-origins \"-\" --declare-type"))
                    .hasRootCauseInstanceOf(ApplicationException.class)
                    .rootCause()
                    .hasMessageContaining("client.redirect-uris.no-wildcard");
            assertThat(this.root.resolve("demo/20-clients.yaml")).doesNotExist();
        });
    }
}
