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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Objects;

/** What a client secret is written as: a variable, or nothing at all. */
public sealed interface SecretAnswer {

    /**
     * The secret is a variable, given at import.
     *
     * @throws HardCodedSecretException when the value is not a single variable
     */
    record Variable(TemplatedValue value) implements SecretAnswer {

        public Variable {
            Objects.requireNonNull(value, "value");
            if (!value.isFullyVariable()) {
                throw new HardCodedSecretException();
            }
        }
    }

    /** No secret is written: Keycloak generates it, and the import keeps it. */
    record None() implements SecretAnswer {}
}
