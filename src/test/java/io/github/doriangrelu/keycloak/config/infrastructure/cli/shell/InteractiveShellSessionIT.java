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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import io.github.doriangrelu.keycloak.config.test.util.StubKeycloak;
import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.ConsoleInputProvider;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.SystemShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The interactive session, driven by the real Spring Shell runner on a console that is a stand-in: what the runner
 * prints when a command fails, where the secret of {@code shell.target} is asked, what the prompt may hold.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class InteractiveShellSessionIT {

    private static final String PASSWORD = "p4ssw0rd-never-printed-88";
    private static final String TOKEN = "/realms/master/protocol/openid-connect/token";
    private static final String SERVER_INFO = "/admin/serverinfo";

    @TempDir
    Path root;

    private StubKeycloak keycloak;
    private int tokenStatus = 200;
    private final Map<String, String> environment = new HashMap<>();
    private final StringWriter printed = new StringWriter();
    private Console console;

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
                                : "{\"error\":\"invalid_grant\"}"));
        this.keycloak.route(
                SERVER_INFO, () -> new StubKeycloak.Answer(200, "{\"systemInfo\":{\"version\":\"26.5.3\"}}"));
        targets(this.keycloak.url());
        this.console = mock(Console.class);
        // the runner closes the writer it prints on, as it does on the real console: each call gets a new one
        when(this.console.writer()).thenAnswer(call -> new PrintWriter(this.printed, true));
    }

    @AfterEach
    void stopTheStub() throws IOException {
        this.keycloak.close();
    }

    private void targets(final String url) throws IOException {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n  local:\n    url: " + url + "\n    user: admin\n    connect-timeout: 3s\n",
                StandardCharsets.UTF_8);
    }

    private ApplicationContextRunner shellContexts() {
        return this.contexts
                .withBean(EnvironmentVariables.class, () -> this.environment::get)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** Launches an interactive session that types these lines, then ends (end of input); the exit code. */
    private int interactive(final ApplicationContext context, final String target, final String... lines) {
        Deque<String> typed = new ArrayDeque<>(List.of(lines));
        when(this.console.readLine(anyString(), any(Object[].class))).thenAnswer(call -> typed.poll());
        ShellSession session = context.getBean(ShellSession.class);
        SystemShellRunner runner = new SystemShellRunner(
                new ShellInputProvider(new ShellPrompt(session), this.console),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("runner", runner);
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(this.root, null, null, null, null, target),
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                session,
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                beans.getBeanProvider(ShellRunner.class),
                new PrintWriter(this.printed, true),
                context.getBean(ConnectionCommands.class));
        launcher.run(new DefaultApplicationArguments());
        return launcher.getExitCode();
    }

    @Test
    void theRealRunnerReadsTheCommandsThroughTheInputProviderOfTheShell() {
        shellContexts().run(context -> {
            assertThat(context.getBeansOfType(ConsoleInputProvider.class)).hasSize(1);
            assertThat(context.getBean(ConsoleInputProvider.class)).isInstanceOf(ShellInputProvider.class);
            ShellRunner runner = context.getBean(ShellRunner.class);
            assertThat(runner).isInstanceOf(SystemShellRunner.class);
            assertThat(ReflectionTestUtils.getField(runner, "inputProvider"))
                    .isSameAs(context.getBean(ConsoleInputProvider.class));
        });
    }

    @Test
    void aRefusedLoginIsExplainedInTheSessionNotByTheHttpStatusAlone() {
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);
        this.tokenStatus = 401;

        shellContexts().run(context -> {
            interactive(context, null, "connect local");

            assertThat(this.printed.toString())
                    .contains("Unable to run command connect : Keycloak at " + this.keycloak.url())
                    .contains("refused the login to target 'local'")
                    .doesNotContain(PASSWORD);
        });
    }

    @Test
    void anUnreachableKeycloakIsExplainedInTheSessionNotByTheHostAlone() throws IOException {
        this.environment.put("KCC_LOCAL_PASSWORD", PASSWORD);
        targets("http://kcc-unknown-host.invalid:8080");

        shellContexts().run(context -> {
            interactive(context, null, "connect local");

            assertThat(this.printed.toString())
                    .contains("Unable to run command connect : Cannot reach Keycloak at")
                    .contains("(target 'local')")
                    .doesNotContain(PASSWORD);
        });
    }

    @Test
    void theSecretOfTheTargetAtStartIsAskedOnTheConsoleOfTheSession() {
        when(this.console.readPassword(anyString())).thenReturn(PASSWORD.toCharArray());

        shellContexts().run(context -> {
            int exitCode = interactive(context, "local");

            assertThat(exitCode).isZero();
            assertThat(this.printed.toString())
                    .contains("Connected to local (" + this.keycloak.url() + ", Keycloak 26.5.3)")
                    .doesNotContain(PASSWORD);
            assertThat(this.keycloak.requestsTo(TOKEN))
                    .singleElement()
                    .satisfies(request -> assertThat(request.body()).contains(PASSWORD));
        });
    }

    @Test
    void aPercentSignInThePromptIsNeverAFormat() {
        shellContexts().run(context -> {
            context.getBean(ShellSession.class).open("100%d-folder");

            interactive(context, null);

            verify(this.console).readLine("%s", "kcc:100%d-folder> ");
        });
    }

    @Test
    void aParseErrorInTheSessionNeverShowsTheValueOfAVar() {
        String secret = "ab'cd-s3cr3t-marker-of-a-session";
        shellContexts().run(context -> {
            assertThat(ReflectionTestUtils.getField(context.getBean(ShellRunner.class), "commandParser"))
                    .isInstanceOf(SafeCommandParser.class);

            interactive(context, null, "realm dry-run --var shopSecret=" + secret);

            assertThat(this.printed.toString())
                    .contains("Error while parsing command: Unbalanced quote (') in input: realm dry-run --var"
                            + " shopSecret=***")
                    .doesNotContain("s3cr3t-marker");
        });
    }
}
