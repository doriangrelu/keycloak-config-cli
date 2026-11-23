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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.NormalizationConfigProperties;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** The readers of the files: import files for an import, exports for a normalization. */
@Configuration(proxyBeanMethods = false)
public class SourceConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
    public ImportFileSource importFileSource(
            final PathMatchingResourcePatternResolver patternResolver, final ImportConfigProperties properties) {
        FileLocations locations = new FileLocations(
                patternResolver,
                properties.getFiles().getExcludes(),
                properties.getFiles().isIncludeHiddenFiles());
        Optional<MustacheTemplates> templates = properties.getMustache().isEnabled()
                ? Optional.of(new MustacheTemplates(properties.getMustache().getVariables()))
                : Optional.empty();
        return new ImportFileReader(locations, templates);
    }

    /** The exports to normalize, never rendered as templates. */
    @Bean
    @ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
    public ExportFileReader exportFileReader(
            final PathMatchingResourcePatternResolver patternResolver, final NormalizationConfigProperties properties) {
        return new ExportFileReader(
                new FileLocations(
                        patternResolver,
                        properties.getFiles().getExcludes(),
                        properties.getFiles().isIncludeHiddenFiles()),
                properties.getFiles().getInputLocations());
    }
}
