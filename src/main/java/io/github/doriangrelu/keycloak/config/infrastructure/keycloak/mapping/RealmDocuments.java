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
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

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
            throw new InvalidImportException(
                    "Unable to parse file '" + file.name() + "': " + e.getMessage(), e, safeMessageOf(file, e));
        }
    }

    /**
     * The failure without the content of the file, which the messages of the parsers quote: the line and the column of
     * a YAML or JSON error, the path of a value that does not fit, or the name of an unknown property.
     */
    static String safeMessageOf(final ImportFile file, final RuntimeException failure) {
        String unparsable = "Unable to parse file '" + file.name() + "'";
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof MarkedYAMLException yaml) {
                Mark mark = yaml.getProblemMark() != null ? yaml.getProblemMark() : yaml.getContextMark();
                return mark == null
                        ? unparsable
                        : unparsable + " at line " + (mark.getLine() + 1) + ", column " + (mark.getColumn() + 1);
            }
            if (current instanceof UnrecognizedPropertyException unknown) {
                String parent = pathOf(unknown.getPath()
                        .subList(0, Math.max(0, unknown.getPath().size() - 1)));
                return unparsable + ": unknown property '" + unknown.getPropertyName() + "'"
                        + (parent.isEmpty() ? "" : " at " + parent);
            }
            if (current instanceof JsonMappingException mapping) {
                String path = pathOf(mapping.getPath());
                return path.isEmpty() ? unparsable : unparsable + ": invalid value at " + path;
            }
        }
        return unparsable;
    }

    /** The path of a value, such as {@code clients[0].secret}: names and indexes only. */
    private static String pathOf(final List<JsonMappingException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
