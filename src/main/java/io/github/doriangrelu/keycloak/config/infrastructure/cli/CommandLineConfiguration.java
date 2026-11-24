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

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

import io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.RunConfigProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The operation to run: {@code run.operation}, an import by default. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RunConfigProperties.class)
public class CommandLineConfiguration {

    /**
     * Spring Shell starts an interactive shell after the application has started, unless a bean of this name exists.
     * Outside the SHELL operation, this bean does nothing: an import or a normalization must never wait for a
     * command on the standard input.
     */
    @Bean
    @ConditionalOnExpression("'${run.operation:IMPORT}' != 'SHELL'")
    public ApplicationRunner springShellApplicationRunner() {
        return arguments -> {};
    }
}
