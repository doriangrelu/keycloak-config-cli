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

import java.io.PrintWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/** The shell command (ADR-008): a second input adapter, next to the import and the normalization. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "SHELL")
@EnableConfigurationProperties(ShellProperties.class)
public class ShellCommandConfiguration {

    /** Named as the runner of Spring Shell, which is then not registered. */
    @Bean
    public ShellLauncher springShellApplicationRunner(
            final ShellProperties properties,
            final CommandParser commandParser,
            final CommandRegistry commandRegistry,
            final ObjectProvider<ShellRunner> interactiveShell) {
        return new ShellLauncher(
                properties, commandParser, commandRegistry, interactiveShell, new PrintWriter(System.out, true));
    }
}
