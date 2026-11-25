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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The keys of an element and their values. A key that is absent or null in the file is not in the map.
 *
 * @param values the values by key
 */
public record ElementKeys(Map<KeyPath, KeyValue> values) {

    public ElementKeys {
        values = Map.copyOf(values);
    }

    public static ElementKeys empty() {
        return new ElementKeys(Map.of());
    }

    public Optional<KeyValue> get(final KeyPath key) {
        return Optional.ofNullable(this.values.get(key));
    }

    /** The keys of this element completed by {@code added}, whose keys replace the existing ones. */
    public ElementKeys with(final ElementKeys added) {
        Map<KeyPath, KeyValue> merged = new LinkedHashMap<>(this.values);
        merged.putAll(added.values);
        return new ElementKeys(merged);
    }
}
