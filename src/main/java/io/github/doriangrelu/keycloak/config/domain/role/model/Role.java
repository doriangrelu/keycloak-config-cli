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

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A realm or client role. Composites are not part of the role yet: they are still imported by the legacy services
 * (EPIC-002, US-002-05).
 *
 * <p>In a desired role, a {@code null} field is not specified by the import file: the value in Keycloak is kept.
 *
 * @param description description, or {@code null} when not specified
 * @param attributes attributes, or {@code null} when not specified; when specified, they replace all the attributes
 */
public record Role(RoleKey key, String description, Map<String, List<String>> attributes, UnmodeledAttributes unmodeled)
        implements Resource<RoleKey> {

    /** Realm roles Keycloak creates in every realm, besides {@code default-roles-<realm>}. */
    private static final Set<String> REALM_ROLES = Set.of("offline_access", "uma_authorization");

    private static final String DEFAULT_ROLES_PREFIX = "default-roles-";

    public Role {
        Objects.requireNonNull(key, "key");
        attributes = attributes == null ? null : copyOf(attributes);
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    @Override
    public String protectedName() {
        return this.key.name();
    }

    public String name() {
        return this.key.name();
    }

    /** This role once {@code desired} is applied on it: fields not specified by {@code desired} keep their value. */
    public Role mergedWith(final Role desired) {
        if (!this.key.equals(desired.key)) {
            throw new IllegalArgumentException("Cannot merge role " + desired.key + " into " + this.key);
        }
        return new Role(
                this.key,
                desired.description != null ? desired.description : this.description,
                desired.attributes != null ? desired.attributes : this.attributes,
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    /**
     * Roles created by Keycloak: the default roles of a realm ({@code default-roles-<realm>}, {@code offline_access},
     * {@code uma_authorization}...), recognized by their translated description, and the roles of the Keycloak
     * clients.
     */
    public boolean isOwnedByKeycloak(final String realm) {
        if (this.key.belongsToKeycloakClient()) {
            return true;
        }
        String name = this.key.name();
        if (this.key.clientId() == null
                && (REALM_ROLES.contains(name)
                        || name.equals(DEFAULT_ROLES_PREFIX + realm.toLowerCase(java.util.Locale.ROOT)))) {
            return true;
        }
        if (name.startsWith(DEFAULT_ROLES_PREFIX) && "${role_default-roles}".equals(this.description)) {
            return true;
        }
        return ("${role_" + name + "}").equals(this.description)
                || ("${role_" + name.replace('_', '-') + "}").equals(this.description);
    }

    private static Map<String, List<String>> copyOf(final Map<String, List<String>> attributes) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        attributes.forEach((name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
