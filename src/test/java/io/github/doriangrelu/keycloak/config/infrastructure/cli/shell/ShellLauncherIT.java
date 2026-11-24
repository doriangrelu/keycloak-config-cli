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
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/** The SHELL operation (ADR-008): a session or one command, and never a shell for the other operations. */
class ShellLauncherIT {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
            .withUserConfiguration(CommandLineConfiguration.class, ShellCommandConfiguration.class);

    @Test
    void importModeRegistersAnInactiveShellRunner() {
        this.contexts.withPropertyValues("run.operation=IMPORT").run(context -> {
            ApplicationRunner runner = context.getBean("springShellApplicationRunner", ApplicationRunner.class);

            assertThat(runner).isNotInstanceOf(ShellLauncher.class);
            runner.run(new DefaultApplicationArguments());
        });
    }

    @Test
    void normalizeModeRegistersAnInactiveShellRunner() {
        this.contexts.withPropertyValues("run.operation=NORMALIZE").run(context -> {
            ApplicationRunner runner = context.getBean("springShellApplicationRunner", ApplicationRunner.class);

            assertThat(runner).isNotInstanceOf(ShellLauncher.class);
            runner.run(new DefaultApplicationArguments());
        });
    }

    @Test
    void shellModeRegistersTheLauncher() {
        this.contexts
                .withPropertyValues("run.operation=SHELL")
                .run(context -> assertThat(context.getBean("springShellApplicationRunner"))
                        .isInstanceOf(ShellLauncher.class));
    }

    @Test
    void scriptModeRunsOneCommandAndExits() {
        this.contexts.withPropertyValues("run.operation=SHELL").run(context -> {
            StringWriter output = new StringWriter();
            ShellLauncher launcher = launcher(context, "help", output);

            launcher.run(new DefaultApplicationArguments());

            assertThat(launcher.getExitCode()).isZero();
            assertThat(output.toString()).contains("help");
        });
    }

    @Test
    void scriptModeUnknownCommandIsInvalidConfiguration() {
        this.contexts.withPropertyValues("run.operation=SHELL").run(context -> {
            ShellLauncher launcher = launcher(context, "nope", new StringWriter());

            launcher.run(new DefaultApplicationArguments());

            assertThat(launcher.getExitCode()).isEqualTo(2);
        });
    }

    private static ShellLauncher launcher(
            final ApplicationContext context, final String command, final StringWriter output) {
        ShellProperties properties = new ShellProperties(null, command);
        return new ShellLauncher(
                properties,
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(org.springframework.shell.core.ShellRunner.class),
                new PrintWriter(output, true));
    }
}
