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

package io.github.doriangrelu.keycloak.config.domain.common;

/**
 * The same resource is defined twice for a realm, with different content (ADR-004: the files of a realm are merged,
 * and a key may only be defined once, or several times identically).
 */
public class ConflictingDefinitionException extends DomainException {

    public ConflictingDefinitionException(final String realm, final String type, final Object key) {
        super(String.format("%s '%s' is defined several times with different content in realm '%s'", type, key, realm));
    }
}
