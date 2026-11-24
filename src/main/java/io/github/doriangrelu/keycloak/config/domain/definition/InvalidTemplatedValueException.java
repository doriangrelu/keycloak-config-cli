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

import io.github.doriangrelu.keycloak.config.domain.common.DomainException;

/** A value holds a placeholder that is not valid Mustache for keycloak-config-cli (ADR-008). */
public final class InvalidTemplatedValueException extends DomainException {

    public InvalidTemplatedValueException(final String value, final String reason) {
        super("Invalid value '" + value + "': " + reason
                + ". Write a variable as {{name}}, or {{name:default}} with a default value");
    }
}
