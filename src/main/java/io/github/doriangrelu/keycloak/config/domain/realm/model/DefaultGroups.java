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

package io.github.doriangrelu.keycloak.config.domain.realm.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The groups, by path, a realm gives to its new users. The declared groups are the exact ones. */
public record DefaultGroups(Set<String> paths) implements Resource<String> {

    /** Key of the only default groups of a realm. */
    public static final String KEY = "default-groups";

    public DefaultGroups {
        paths = paths == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(paths));
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String stateName() {
        return KEY;
    }
}
