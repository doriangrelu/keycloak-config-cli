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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Identity of a role: its name, and the client id for a client role.
 *
 * @param clientId client id of a client role, {@code null} for a realm role
 */
public record RoleKey(String clientId, String name) {

    /** Clients created by Keycloak in every realm: their roles belong to Keycloak. */
    private static final Set<String> KEYCLOAK_CLIENTS = Set.of("realm-management", "account", "broker", "master-realm");

    public RoleKey {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A role needs a name");
        }
    }

    public static RoleKey realm(final String name) {
        return new RoleKey(null, name);
    }

    public static RoleKey client(final String clientId, final String name) {
        return new RoleKey(Objects.requireNonNull(clientId, "clientId"), name);
    }

    public boolean isClientRole() {
        return this.clientId != null;
    }

    public Optional<String> client() {
        return Optional.ofNullable(this.clientId);
    }

    public boolean belongsToKeycloakClient() {
        return this.clientId != null && KEYCLOAK_CLIENTS.contains(this.clientId);
    }

    @Override
    public String toString() {
        return this.clientId == null ? this.name : this.clientId + "/" + this.name;
    }
}
