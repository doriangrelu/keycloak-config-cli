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

import java.util.List;

/**
 * A variable of a realm: its default values, distinct and sorted, and the files that use it, sorted.
 *
 * @param name the name of the variable
 * @param defaultValues the distinct default values written in the placeholders
 * @param files the files that hold a placeholder of the variable
 * @param usedWithoutDefault whether a placeholder of the variable has no default value
 */
public record VariableUse(String name, List<String> defaultValues, List<String> files, boolean usedWithoutDefault) {

    public VariableUse {
        defaultValues = List.copyOf(defaultValues);
        files = List.copyOf(files);
    }

    /** A variable used without default value only when no placeholder gives one. */
    public VariableUse(final String name, final List<String> defaultValues, final List<String> files) {
        this(name, defaultValues, files, defaultValues.isEmpty());
    }

    /** Whether the placeholders give different default values: the import would use only one of them. */
    public boolean hasConflictingDefaults() {
        return this.defaultValues.size() > 1;
    }
}
