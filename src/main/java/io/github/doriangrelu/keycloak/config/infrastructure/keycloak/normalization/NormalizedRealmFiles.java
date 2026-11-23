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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Writes the normalized realms, one file per realm, named after it. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
public class NormalizedRealmFiles {

    private final YAMLMapper yamlMapper;
    private final ObjectMapper objectMapper;

    public NormalizedRealmFiles(final YAMLMapper yamlMapper, final ObjectMapper objectMapper) {
        this.yamlMapper = Objects.requireNonNull(yamlMapper, "yamlMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * @param yaml whether the file is written in YAML, or else in JSON
     * @return the file written
     */
    public Path write(final RealmRepresentation realm, final Path directory, final boolean yaml) throws IOException {
        Path file = directory.resolve(realm.getRealm() + (yaml ? ".yaml" : ".json"));
        try (OutputStream output = Files.newOutputStream(file)) {
            (yaml ? this.yamlMapper : this.objectMapper).writeValue(output, realm);
        }
        return file;
    }
}
