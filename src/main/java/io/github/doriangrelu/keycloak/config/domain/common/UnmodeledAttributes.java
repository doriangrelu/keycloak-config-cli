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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fields of a Keycloak resource that the domain does not model, carried as is (ADR-002). No domain rule reads them:
 * they are compared as a whole and written back unchanged.
 *
 * <p>Values are restricted to simple JSON types: strings, numbers, booleans, and lists or maps of those. A
 * {@code null} value is kept: it means "explicitly empty".
 */
public record UnmodeledAttributes(Map<String, Object> values) {

    private static final UnmodeledAttributes NONE = new UnmodeledAttributes(Map.of());

    public UnmodeledAttributes {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        values.forEach(UnmodeledAttributes::requireSimpleJson);
    }

    public static UnmodeledAttributes none() {
        return NONE;
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    /** Applies the given attributes on top of these ones: keys present in {@code overrides} win. */
    public UnmodeledAttributes overriddenBy(final UnmodeledAttributes overrides) {
        if (overrides.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(this.values);
        merged.putAll(overrides.values);
        return new UnmodeledAttributes(merged);
    }

    private static void requireSimpleJson(final String key, final Object value) {
        switch (value) {
            case null -> {}
            case String _, Number _, Boolean _ -> {}
            case List<?> list -> list.forEach(item -> requireSimpleJson(key, item));
            case Map<?, ?> map -> map.forEach((nestedKey, nested) -> requireSimpleJson(key, nested));
            default ->
                throw new IllegalArgumentException("Unsupported value for attribute '" + key + "': "
                        + value.getClass().getName());
        }
    }
}
