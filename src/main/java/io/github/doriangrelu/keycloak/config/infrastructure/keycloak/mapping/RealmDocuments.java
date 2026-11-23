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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses the realms of a file, in the format of Keycloak: a JSON document, or one or several YAML documents. Unknown
 * properties are rejected, so that a misspelt key is not silently ignored.
 */
public final class RealmDocuments {

    private static final Logger logger = LoggerFactory.getLogger(RealmDocuments.class);

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private RealmDocuments() {}

    /** @throws InvalidImportException when the file is not a valid document of {@code type} */
    public static <T> List<T> parse(final ImportFile file, final Class<T> type) {
        if (logger.isTraceEnabled()) {
            logger.trace(file.content());
        }
        try {
            List<T> realms = new ArrayList<>();
            for (Object document : new Yaml().loadAll(file.content())) {
                realms.add(OBJECT_MAPPER.convertValue(document, type));
            }
            return realms;
        } catch (RuntimeException e) {
            throw new InvalidImportException("Unable to parse file '" + file.name() + "': " + e.getMessage(), e);
        }
    }
}
