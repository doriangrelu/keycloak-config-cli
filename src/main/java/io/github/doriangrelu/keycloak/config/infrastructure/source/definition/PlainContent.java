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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import org.yaml.snakeyaml.Yaml;

/** The content of a definition file without its layout: what must not change when it is formatted. */
final class PlainContent {

    private PlainContent() {}

    static Object of(final String fileName, final String content) {
        DefinitionFormat format = DefinitionFormat.ofFileName(fileName).orElseThrow();
        if (format == DefinitionFormat.YAML) {
            return new Yaml().load(content);
        }
        try {
            return new ObjectMapper().readTree(content);
        } catch (JsonProcessingException e) {
            throw new UnreadableDocumentException(1, e.getOriginalMessage());
        }
    }
}
