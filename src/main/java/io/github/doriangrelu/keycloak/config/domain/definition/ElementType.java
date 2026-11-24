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

/** The elements the shell edits, with their key in the export format and their standard file (ADR-008). */
public enum ElementType {
    CLIENT("clients", "20-clients"),
    ROLE("roles", "30-roles"),
    GROUP("groups", "40-groups");

    private final String key;
    private final String fileName;

    ElementType(final String key, final String fileName) {
        this.key = key;
        this.fileName = fileName;
    }

    /** The key of the export format that declares the elements of this type. */
    public String key() {
        return this.key;
    }

    /** The name of the standard file of this type, without extension. */
    public String fileName() {
        return this.fileName;
    }
}
