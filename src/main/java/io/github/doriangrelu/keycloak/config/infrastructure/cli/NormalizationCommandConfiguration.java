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

import io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.NormalizationConfigProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.NormalizationKeycloakConfigProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.NormalizationOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.source.ExportFileReader;
import io.github.doriangrelu.keycloak.config.infrastructure.source.FileLocations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** The normalization command: binds the {@code normalization.*} and {@code normalize.*} properties. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
@EnableConfigurationProperties({NormalizationConfigProperties.class, NormalizationKeycloakConfigProperties.class})
public class NormalizationCommandConfiguration {

    /** The exports to normalize, never rendered as templates. */
    @Bean
    public ExportFileReader exportFileReader(
            final PathMatchingResourcePatternResolver patternResolver, final NormalizationConfigProperties properties) {
        return new ExportFileReader(
                new FileLocations(
                        patternResolver,
                        properties.getFiles().getExcludes(),
                        properties.getFiles().isIncludeHiddenFiles()),
                properties.getFiles().getInputLocations());
    }

    @Bean
    public NormalizationOptions normalizationOptions(
            final NormalizationKeycloakConfigProperties keycloak, final NormalizationConfigProperties properties) {
        return new NormalizationOptions(keycloak.getVersion(), properties.getFallbackVersion());
    }
}
