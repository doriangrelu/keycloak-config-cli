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

package io.github.doriangrelu.keycloak.config.domain.role.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Roles included in a composite role, by the role they belong to. A declared part replaces the composites of that
 * part: {@code realm} replaces the realm role composites, {@code client} replaces the client role composites of every
 * client, a client missing from it losing its composites.
 *
 * @param key the composite role
 * @param realm names of the included realm roles, or {@code null} when not specified
 * @param client names of the included client roles by client id, or {@code null} when not specified
 */
public record RoleComposites(RoleKey key, Set<String> realm, Map<String, Set<String>> client)
        implements Resource<RoleKey> {

    public RoleComposites {
        Objects.requireNonNull(key, "key");
        realm = realm == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(realm));
        client = client == null ? null : withoutEmptyClients(client);
    }

    public static RoleComposites none(final RoleKey key) {
        return new RoleComposites(key, Set.of(), Map.of());
    }

    @Override
    public String stateName() {
        return this.key.name();
    }

    /** These composites once {@code desired} is applied: parts not specified by {@code desired} are kept. */
    public RoleComposites mergedWith(final RoleComposites desired) {
        if (!this.key.equals(desired.key)) {
            throw new IllegalArgumentException("Cannot merge composites of " + desired.key + " into " + this.key);
        }
        return new RoleComposites(
                this.key,
                desired.realm != null ? desired.realm : this.realm,
                desired.client != null ? desired.client : this.client);
    }

    /** Every included role; parts not specified count as empty. */
    public Set<RoleKey> roles() {
        Set<RoleKey> roles = new LinkedHashSet<>();
        if (this.realm != null) {
            this.realm.forEach(name -> roles.add(RoleKey.realm(name)));
        }
        if (this.client != null) {
            this.client.forEach((clientId, names) -> names.forEach(name -> roles.add(RoleKey.client(clientId, name))));
        }
        return Collections.unmodifiableSet(roles);
    }

    /** A client declared with no role has no composite: it is dropped, so that it compares equal to Keycloak. */
    private static Map<String, Set<String>> withoutEmptyClients(final Map<String, Set<String>> client) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        client.forEach((clientId, names) -> {
            if (names != null && !names.isEmpty()) {
                copy.put(clientId, Collections.unmodifiableSet(new LinkedHashSet<>(names)));
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
