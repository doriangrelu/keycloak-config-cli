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

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The operators of a check, written in kebab case in the rules ({@code one-of}). */
public enum Operator {
    EQUALS,
    ONE_OF,
    NOT_ONE_OF,
    MATCHES,
    NONE_MATCHES,
    PRESENT,
    ABSENT;

    public static Optional<Operator> named(final String name) {
        return Arrays.stream(values())
                .filter(operator -> operator.writtenName().equals(name))
                .findFirst();
    }

    private String writtenName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
