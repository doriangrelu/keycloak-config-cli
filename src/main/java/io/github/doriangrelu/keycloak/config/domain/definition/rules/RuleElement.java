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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;

/** The kind of element a rule applies to. */
public enum RuleElement {
    REALM,
    CLIENT,
    ROLE,
    GROUP;

    public static RuleElement of(final ElementType type) {
        return switch (type) {
            case CLIENT -> CLIENT;
            case ROLE -> ROLE;
            case GROUP -> GROUP;
        };
    }

    /** The key that names an element of this kind. */
    public KeyPath identifierKey() {
        return KeyPath.parse(
                switch (this) {
                    case REALM -> "realm";
                    case CLIENT -> "clientId";
                    case ROLE -> "name";
                    case GROUP -> "path";
                });
    }
}
