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

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import org.testcontainers.containers.GenericContainer;

/** {@code connect} through the real launcher, against the Keycloak of the integration tests. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConnectionCommandsKeycloakIT {

    private static final GenericContainer<?> KEYCLOAK = AbstractImportIT.KEYCLOAK_CONTAINER;
    private static final String URL = System.getProperty("keycloak.url");
    private static final String ADMIN = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN");
    private static final String ADMIN_PASSWORD = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN_PASSWORD");

    @TempDir
    Path root;

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
            .withUserConfiguration(
                    CommandLineConfiguration.class, ShellCommandConfiguration.class, ShellAdaptersConfiguration.class);

    private ApplicationContextRunner shellContexts(final Map<String, String> environment) throws Exception {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n  local:\n    url: " + URL + "\n    user: " + ADMIN + "\n",
                StandardCharsets.UTF_8);
        return this.contexts
                .withBean(EnvironmentVariables.class, () -> environment::get)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    private int launch(final ApplicationContext context, final String command, final StringWriter output) {
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(this.root, null, command, null, null, null),
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                context.getBean(ShellSession.class),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(ShellRunner.class),
                new PrintWriter(output, true),
                context.getBean(ConnectionCommands.class));
        launcher.run(new DefaultApplicationArguments());
        return launcher.getExitCode();
    }

    @Test
    void connectWithTheEnvironmentPassword() throws Exception {
        shellContexts(Map.of("KCC_LOCAL_PASSWORD", ADMIN_PASSWORD)).run(context -> {
            StringWriter output = new StringWriter();

            assertThat(launch(context, "connect local", output)).isZero();

            assertThat(output.toString())
                    .contains("Connected to local (" + URL + ", Keycloak " + System.getProperty("keycloak.version"))
                    .doesNotContain(ADMIN_PASSWORD);
        });
    }

    @Test
    void aWrongPasswordGivesExitCode4() throws Exception {
        String wrong = "not-the-password-4242";
        shellContexts(Map.of("KCC_LOCAL_PASSWORD", wrong)).run(context -> {
            StringWriter output = new StringWriter();

            assertThat(launch(context, "connect local", output)).isEqualTo(4);

            assertThat(output.toString()).doesNotContain(wrong);
        });
    }
}
