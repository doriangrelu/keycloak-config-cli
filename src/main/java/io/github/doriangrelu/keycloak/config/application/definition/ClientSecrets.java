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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The current secrets of the clients of a realm, by the name of their variable ({@code shopSecret}). Read only; its
 * text gives the names, never a value.
 */
public final class ClientSecrets extends AbstractMap<String, String> {

    private final Map<String, String> secrets;
    private final Set<Entry<String, String>> entries;

    private ClientSecrets(final Map<String, String> secrets) {
        this.secrets = Collections.unmodifiableMap(new TreeMap<>(secrets));
        Set<Entry<String, String>> named = new LinkedHashSet<>();
        this.secrets.forEach((name, value) -> named.add(new Secret(name, value)));
        this.entries = Collections.unmodifiableSet(named);
    }

    /** A copy of the given secrets, sorted by name. */
    public static ClientSecrets of(final Map<String, String> secrets) {
        return new ClientSecrets(secrets);
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return this.entries;
    }

    @Override
    public String get(final Object name) {
        return this.secrets.get(name);
    }

    /** The names only. */
    @Override
    public String toString() {
        return "ClientSecrets" + this.secrets.keySet();
    }

    /** An entry whose text gives the name only. */
    private static final class Secret extends SimpleImmutableEntry<String, String> {

        private Secret(final String name, final String value) {
            super(name, value);
        }

        @Override
        public String toString() {
            return getKey();
        }
    }
}
