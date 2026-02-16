/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
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

package io.github.doriangrelu.keycloak.config.mustache;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A Mustache context that supports default values using the syntax {@code {{key:default}}}.
 * <p>
 * If a variable name contains a colon, the part before the colon is used as the lookup key
 * and the part after as the default value when the key is not found in the variables map.
 * Variables without a colon fall back to the compiler's {@code defaultValue("")}.
 */
public class MustacheContextWithDefaults extends AbstractMap<String, Object> {

    private static final char DEFAULT_VALUE_SEPARATOR_CHAR = ':';
    private static final int BEGIN_INDEX = 0;

    private final Map<String, String> variables;

    public MustacheContextWithDefaults(final Map<String, String> variables) {
        this.variables = new ConcurrentHashMap<>(variables);
    }

    @Override
    public Object get(final Object key) {
        final String keyStr = null == key ? "" : key.toString();
        if (this.variables.containsKey(keyStr)) {
            return this.variables.get(keyStr);
        }
        final int colonIdx = keyStr.indexOf(DEFAULT_VALUE_SEPARATOR_CHAR);
        if (colonIdx >= BEGIN_INDEX) {
            final String varName = keyStr.substring(BEGIN_INDEX, colonIdx);
            final String defaultValue = keyStr.substring(colonIdx + 1);
            return this.variables.getOrDefault(varName, defaultValue);
        }
        return null;
    }

    @Override
    public boolean containsKey(final Object key) {
        final String keyStr = key.toString();
        return this.variables.containsKey(keyStr) || keyStr.contains(String.valueOf(DEFAULT_VALUE_SEPARATOR_CHAR));
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return this.variables.entrySet().stream()
                .map(entry -> new SimpleImmutableEntry<>(entry.getKey(), (Object) entry.getValue()))
                .collect(Collectors.toSet());
    }

}
