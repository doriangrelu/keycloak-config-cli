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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values Keycloak hides: it answers a mask instead of a secret it keeps (the SMTP password, the secret of an
 * identity provider, the bind credential of a user storage...). The current value is then unknown: it cannot be
 * compared with the value of the files.
 */
public final class HiddenValues {

    /** What Keycloak answers instead of a secret. */
    public static final String MASK = "**********";

    private HiddenValues() {}

    /** Whether the value is the mask, alone or as the single value of a list (a component configuration). */
    public static boolean isHidden(final Object value) {
        return MASK.equals(value) || (value instanceof List<?> list && list.size() == 1 && MASK.equals(list.get(0)));
    }

    /** The keys {@code declared} gives a value to and that Keycloak hides in {@code current}, in declared order. */
    public static List<String> hiddenKeys(final Map<?, ?> current, final Map<?, ?> declared) {
        List<String> hidden = new ArrayList<>();
        if (current == null || declared == null) {
            return hidden;
        }
        declared.forEach((key, value) -> {
            if (value != null && isHidden(current.get(key))) {
                hidden.add(String.valueOf(key));
            }
        });
        return hidden;
    }

    /**
     * The target as it compares with {@code current}: each value Keycloak hides in {@code current} is taken from
     * {@code current}. The same instance when there is none.
     */
    public static <K, V> Map<K, V> withHiddenValuesOf(final Map<K, V> current, final Map<K, V> target) {
        if (current == null || target == null) {
            return target;
        }
        Map<K, V> comparable = null;
        for (Map.Entry<K, V> entry : target.entrySet()) {
            V currentValue = current.get(entry.getKey());
            if (entry.getValue() != null && isHidden(currentValue) && !currentValue.equals(entry.getValue())) {
                if (comparable == null) {
                    comparable = new LinkedHashMap<>(target);
                }
                comparable.put(entry.getKey(), currentValue);
            }
        }
        return comparable == null ? target : comparable;
    }

    /** The map held under {@code key}, or {@code null}. */
    @SuppressWarnings("unchecked")
    public static Map<Object, Object> mapOf(final UnmodeledAttributes attributes, final String key) {
        return attributes.values().get(key) instanceof Map<?, ?> map ? (Map<Object, Object>) map : null;
    }

    /**
     * The attributes, the map under {@code key} made comparable with the one of {@code current}; the same instance
     * when nothing is hidden.
     */
    public static UnmodeledAttributes withHiddenValuesOf(
            final UnmodeledAttributes current, final UnmodeledAttributes target, final String key) {
        Map<Object, Object> targetMap = mapOf(target, key);
        Map<Object, Object> comparable = withHiddenValuesOf(mapOf(current, key), targetMap);
        if (comparable == targetMap) {
            return target;
        }
        Map<String, Object> values = new LinkedHashMap<>(target.values());
        values.put(key, comparable);
        return new UnmodeledAttributes(values);
    }

    /** The hidden keys of the map under {@code key}, named {@code key.name}. */
    public static List<String> hiddenFields(
            final UnmodeledAttributes current, final UnmodeledAttributes declared, final String key) {
        return hiddenKeys(mapOf(current, key), mapOf(declared, key)).stream()
                .map(name -> key + "." + name)
                .toList();
    }
}
