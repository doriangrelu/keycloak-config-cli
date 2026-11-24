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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.util.Locale;
import java.util.Optional;

/** The format of a definition file. */
public enum DefinitionFormat {
    YAML(".yaml"),
    JSON(".json");

    private final String extension;

    DefinitionFormat(final String extension) {
        this.extension = extension;
    }

    public String extension() {
        return this.extension;
    }

    /** The format of a file, by its extension ({@code .yaml}, {@code .yml} or {@code .json}). */
    public static Optional<DefinitionFormat> ofFileName(final String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return Optional.of(YAML);
        }
        if (name.endsWith(".json")) {
            return Optional.of(JSON);
        }
        return Optional.empty();
    }
}
