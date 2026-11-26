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
import io.github.doriangrelu.keycloak.config.test.util.StubKeycloak;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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

/**
 * {@code connect}, {@code disconnect} and {@code target list} through the real launcher, on the real adapters, against
 * a stub of the Keycloak endpoints: no Keycloak is needed (see {@code ConnectionCommandsKeycloakIT} for a real one).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConnectionCommandsIT {

    private static final String PASSWORD = "p4ssw0rd-never-printed-77";
    private static final String TOKEN = "/realms/master/protocol/openid-connect/token";
    private static final String SERVER_INFO = "/admin/serverinfo";

    @TempDir
    Path root;

    private StubKeycloak keycloak;
    private int tokenStatus = 200;
    private final Map<String, String> environment = new HashMap<>();

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
            .withUserConfiguration(
                    CommandLineConfiguration.class, ShellCommandConfiguration.class, ShellAdaptersConfiguration.class);

    @BeforeEach
    void startAStubOfKeycloak() throws IOException {
        this.keycloak = new StubKeycloak();
        this.keycloak.route(
                TOKEN,
                () -> new StubKeycloak.Answer(
                        this.tokenStatus,
                        this.tokenStatus == 200
                                ? "{\"access_token\":\"stub-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}"
                                : "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}"));
        this.keycloak.route(
                SERVER_INFO, () -> new StubKeycloak.Answer(200, "{\"systemInfo\":{\"version\":\"26.5.3\"}}"));
    }

    @AfterEach
    void stopTheStub() throws IOException {
        this.keycloak.close();
    }

    private void targets(final String url) throws IOException {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n"
                        + "  local:\n"
                        + "    url: " + url + "\n"
                        + "    user: admin\n"
                        + "    connect-timeout: 3s\n"
                        + "  my-dev:\n"
                        + "    url: " + url + "\n"
                        + "    user: admin\n"
                        + "    connect-timeout: 3s\n",
                StandardCharsets.UTF_8);
    }

    private ApplicationContextRunner shellContexts() {
        return this.contexts
                .withBean(EnvironmentVariables.class, () -> this.environment::get)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** What a launch gave: the exit code, what it printed, and whether a connection was still open at the end. */
    private record Outcome(int exitCode, String output, boolean connectedAtTheEnd) {}

    private Outcome launch(
            final ApplicationContext context, final String command, final String script, final String target)
            throws IOException {
        StringWriter output = new StringWriter();
        Path scriptFile = null;
        if (script != null) {
            scriptFile = this.root.resolve("session.kcc");
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
        }
        ShellSession session = context.getBean(ShellSession.class);
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(this.root, null, command, null, scriptFile, target),
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                session,
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(ShellRunner.class),
                new PrintWriter(output, true),
                context.getBean(ConnectionCommands.class));
        launcher.run(new DefaultApplicationArguments());
        return new Outcome(
                launcher.getExitCode(), output.toString(), session.connection().isPresent());
    }

    @Test
    void connectWithTheEnvironmentPassword() throws Exception {
        targets(this.keycloak.url());
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "connect local", null, null);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .contains("Connected to local (" + this.keycloak.url() + ", Keycloak 26.5.3)")
                    .doesNotContain(PASSWORD);
            assertThat(this.keycloak.requestsTo(TOKEN))
                    .singleElement()
                    .satisfies(request -> assertThat(request.body()).contains("username=admin", PASSWORD));
        });
    }

    @Test
    void aWrongPasswordGivesExitCode4() throws Exception {
        targets(this.keycloak.url());
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);
        this.tokenStatus = 401;

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "connect local", null, null);

            assertThat(outcome.exitCode()).isEqualTo(4);
            assertThat(outcome.output())
                    .contains("refused the login")
                    .contains("'local'")
                    .doesNotContain(PASSWORD)
                    .doesNotContain("Exception")
                    .doesNotContain("\tat ");
            assertThat(outcome.connectedAtTheEnd()).isFalse();
        });
    }

    @Test
    void anUnreachableKeycloakGivesExitCode3() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        targets("http://localhost:" + closedPort);
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "connect local", null, null);

            assertThat(outcome.exitCode()).isEqualTo(3);
            assertThat(outcome.output()).contains("Cannot reach Keycloak").doesNotContain(PASSWORD);
        });
    }

    @Test
    void anUnknownTargetGivesExitCode2() throws Exception {
        targets(this.keycloak.url());

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "connect prod", null, null);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Unknown target 'prod'").contains("local, my-dev");
        });
    }

    @Test
    void aMissingSecretInScriptModeGivesExitCode2() throws Exception {
        targets(this.keycloak.url());

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "connect my-dev", null, null);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("KCC_MY_DEV_PASSWORD");
            assertThat(this.keycloak.requestsTo(TOKEN)).isEmpty();
        });
    }

    @Test
    void aTargetsFileProblemGivesExitCode2() throws Exception {
        Files.writeString(this.root.resolve("kcc-targets.yaml"), "targets:\n  a:\n    user: admin\n");

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "target list", null, null);

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("kcc-targets.yaml: target 'a'");
        });
    }

    @Test
    void theShellTargetPropertyConnectsAtStart() throws Exception {
        targets(this.keycloak.url());
        this.environment.put("KCC_MY_DEV_PASSWORD", PASSWORD);

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "target list", null, "my-dev");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .contains("Connected to my-dev")
                    .contains("my-dev  " + this.keycloak.url() + "  password  (connected)")
                    .doesNotContain(PASSWORD);
        });
    }

    @Test
    void theShellTargetPropertyFailsTheLaunchWithTheCodeOfTheCause() throws Exception {
        targets(this.keycloak.url());
        this.environment.put("KCC_MY_DEV_PASSWORD", PASSWORD);
        this.tokenStatus = 401;

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "target list", null, "my-dev");

            assertThat(outcome.exitCode()).isEqualTo(4);
            assertThat(outcome.output()).doesNotContain(PASSWORD).doesNotContain("local  ");
        });
    }

    @Test
    void theShellTargetPropertyNamesAnUnknownTarget() throws Exception {
        targets(this.keycloak.url());

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "target list", null, "nope");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Unknown target 'nope'");
        });
    }

    @Test
    void aScriptConnectsListsAndDisconnects() throws Exception {
        targets(this.keycloak.url());
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);

        shellContexts().run(context -> {
            Outcome outcome = launch(context, null, "target list\nconnect local\ntarget list\ndisconnect\n", null);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .contains("local  " + this.keycloak.url() + "  password" + System.lineSeparator())
                    .contains("local  " + this.keycloak.url() + "  password  (connected)")
                    .contains("Disconnected from local");
            assertThat(outcome.connectedAtTheEnd()).isFalse();
        });
    }

    @Test
    void theConnectionIsClosedWhenTheScriptEnds() throws Exception {
        targets(this.keycloak.url());
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "connect local", null, null);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.connectedAtTheEnd()).isFalse();
        });
    }

    @Test
    void disconnectWithoutConnectionSaysSo() throws Exception {
        targets(this.keycloak.url());

        shellContexts().run(context -> {
            Outcome outcome = launch(context, "disconnect", null, null);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("Not connected: use 'connect <target>'");
        });
    }

    @Test
    void theShellTargetPropertyIsBound() {
        shellContexts()
                .withPropertyValues("shell.target=my-dev")
                .run(context -> assertThat(
                                context.getBean(ShellProperties.class).getTarget())
                        .contains("my-dev"));
    }

    @Test
    void noTargetFileMeansNoTarget() {
        shellContexts().run(context -> {
            Outcome outcome = launch(context, "target list", null, null);

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("No target in kcc-targets.yaml");
        });
    }
}
