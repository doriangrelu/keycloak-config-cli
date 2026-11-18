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

package io.github.doriangrelu.keycloak.config.domain.messagebundle.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The localization texts of a realm for one locale.
 *
 * @param texts texts by key
 */
public record MessageBundle(String locale, Map<String, String> texts) implements Resource<String> {

    public MessageBundle {
        Objects.requireNonNull(locale, "locale");
        texts = texts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    @Override
    public String key() {
        return this.locale;
    }

    @Override
    public String stateName() {
        return this.locale;
    }

    /**
     * These texts once {@code desired} is applied.
     *
     * @param removeMissingKeys whether keys missing from {@code desired} are removed ({@code full} managed mode), or
     *     kept ({@code no-delete})
     */
    public MessageBundle mergedWith(final MessageBundle desired, final boolean removeMissingKeys) {
        if (removeMissingKeys) {
            return new MessageBundle(this.locale, desired.texts);
        }
        Map<String, String> merged = new LinkedHashMap<>(this.texts);
        merged.putAll(desired.texts);
        return new MessageBundle(this.locale, merged);
    }

    /** Texts of {@code target} that are new or changed compared to these ones. */
    public Map<String, String> textsChangedIn(final MessageBundle target) {
        Map<String, String> changed = new LinkedHashMap<>();
        target.texts.forEach((key, value) -> {
            if (!Objects.equals(this.texts.get(key), value)) {
                changed.put(key, value);
            }
        });
        return changed;
    }

    /** Keys of these texts that {@code target} no longer has. */
    public Set<String> keysRemovedIn(final MessageBundle target) {
        Set<String> removed = new LinkedHashSet<>(this.texts.keySet());
        removed.removeAll(target.texts.keySet());
        return removed;
    }
}
