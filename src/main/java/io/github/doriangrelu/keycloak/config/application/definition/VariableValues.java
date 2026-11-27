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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The values of the variables a session has been given for the opened realm and the connected target. They live in
 * memory only: they are never written, shown or logged, and {@link #toString()} shows none of them.
 */
public final class VariableValues {

    private final Map<String, String> values = new HashMap<>();

    public Optional<String> get(final String name) {
        return Optional.ofNullable(this.values.get(name));
    }

    public void remember(final String name, final String value) {
        this.values.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
    }

    /** Forgets every value. */
    public void forget() {
        this.values.clear();
    }

    @Override
    public String toString() {
        return "VariableValues[" + this.values.size() + " value(s)]";
    }
}
