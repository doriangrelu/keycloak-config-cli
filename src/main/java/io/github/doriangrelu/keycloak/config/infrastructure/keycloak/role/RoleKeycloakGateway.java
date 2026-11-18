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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role;

import io.github.doriangrelu.keycloak.config.application.role.UnknownClientException;
import io.github.doriangrelu.keycloak.config.application.role.port.RoleGateway;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.List;
import java.util.Optional;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link RoleGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RoleKeycloakGateway implements RoleGateway {

    private final RoleApi roleApi;
    private final RoleKeycloakMapper mapper;

    public RoleKeycloakGateway(final RoleApi roleApi, final RoleKeycloakMapper mapper) {
        this.roleApi = roleApi;
        this.mapper = mapper;
    }

    @Override
    public List<Role> findRealmRoles(final String realm) {
        return this.roleApi.listRealmRoles(realm).stream()
                .filter(role -> !Boolean.TRUE.equals(role.getClientRole()))
                .map(role -> this.mapper.toRole(role, null))
                .toList();
    }

    @Override
    public Optional<List<Role>> findClientRoles(final String realm, final String clientId) {
        return this.roleApi
                .findClientUuid(realm, clientId)
                .map(clientUuid -> this.roleApi.listClientRoles(realm, clientUuid).stream()
                        .map(role -> this.mapper.toRole(role, clientId))
                        .toList());
    }

    @Override
    public void create(final String realm, final Role role) {
        RoleRepresentation representation = this.mapper.toRepresentation(role);
        if (!role.key().isClientRole()) {
            this.roleApi.createRealmRole(realm, representation);
            return;
        }
        String clientUuid = this.roleApi
                .findClientUuid(realm, role.key().clientId())
                .orElseThrow(() -> new UnknownClientException(realm, role.key().clientId(), role.name()));
        this.roleApi.createClientRole(realm, clientUuid, representation);
        // Attributes are ignored when a client role is created: KEYCLOAK-16082
        this.roleApi.updateClientRole(realm, clientUuid, role.name(), representation);
    }

    @Override
    public void update(final String realm, final Role role) {
        RoleRepresentation representation = this.mapper.toRepresentation(role);
        if (!role.key().isClientRole()) {
            this.roleApi.updateRealmRole(realm, role.name(), representation);
            return;
        }
        String clientUuid = this.roleApi
                .findClientUuid(realm, role.key().clientId())
                .orElseThrow(() -> new UnknownClientException(realm, role.key().clientId(), role.name()));
        this.roleApi.updateClientRole(realm, clientUuid, role.name(), representation);
    }

    @Override
    public void delete(final String realm, final RoleKey key) {
        if (!key.isClientRole()) {
            this.roleApi.deleteRealmRole(realm, key.name());
            return;
        }
        // A client deleted in the same run took its roles with it
        this.roleApi
                .findClientUuid(realm, key.clientId())
                .ifPresent(clientUuid -> this.roleApi.deleteClientRole(realm, clientUuid, key.name()));
    }
}
