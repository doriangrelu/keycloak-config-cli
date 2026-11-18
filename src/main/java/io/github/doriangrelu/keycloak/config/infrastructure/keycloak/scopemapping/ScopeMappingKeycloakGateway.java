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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.scopemapping;

import io.github.doriangrelu.keycloak.config.application.scopemapping.UnknownScopeMappingReferenceException;
import io.github.doriangrelu.keycloak.config.application.scopemapping.port.ScopeMappingGateway;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping.HolderType;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientScopeApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ScopeMappingApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link ScopeMappingGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ScopeMappingKeycloakGateway implements ScopeMappingGateway {

    private final ScopeMappingApi scopeMappingApi;
    private final ClientApi clientApi;
    private final ClientScopeApi clientScopeApi;
    private final RoleApi roleApi;

    public ScopeMappingKeycloakGateway(
            final ScopeMappingApi scopeMappingApi,
            final ClientApi clientApi,
            final ClientScopeApi clientScopeApi,
            final RoleApi roleApi) {
        this.scopeMappingApi = scopeMappingApi;
        this.clientApi = clientApi;
        this.clientScopeApi = clientScopeApi;
        this.roleApi = roleApi;
    }

    @Override
    public List<ScopeMapping> findAll(final String realm, final boolean clientRoles) {
        List<ScopeMapping> mappings = new ArrayList<>();
        if (clientRoles) {
            this.scopeMappingApi
                    .listClientRoleScopeMappings(realm)
                    .forEach((roleClientId, declared) -> declared.forEach(
                            mapping -> toScopeMapping(mapping, roleClientId).ifPresent(mappings::add)));
        } else {
            this.scopeMappingApi
                    .listRealmRoleScopeMappings(realm)
                    .forEach(mapping -> toScopeMapping(mapping, null).ifPresent(mappings::add));
        }
        return mappings;
    }

    private static Optional<ScopeMapping> toScopeMapping(
            final ScopeMappingRepresentation mapping, final String roleClientId) {
        if (mapping.getRoles() == null || mapping.getRoles().isEmpty()) {
            return Optional.empty();
        }
        ScopeMapping.Key key = mapping.getClient() != null
                ? ScopeMapping.Key.ofClient(mapping.getClient(), roleClientId)
                : ScopeMapping.Key.ofClientScope(mapping.getClientScope(), roleClientId);
        return Optional.of(new ScopeMapping(key, mapping.getRoles()));
    }

    @Override
    public void addRoles(final String realm, final ScopeMapping.Key key, final Set<String> roles) {
        String holderId = holderIdOf(realm, key).orElseThrow(() -> unknownHolder(key));
        boolean clientScope = key.holderType() == HolderType.CLIENT_SCOPE;
        if (key.isRealmRoles()) {
            this.scopeMappingApi.addRealmRoles(realm, clientScope, holderId, realmRoles(realm, roles));
        } else {
            String roleClientUuid = this.roleApi
                    .findClientUuid(realm, key.roleClientId())
                    .orElseThrow(() -> UnknownScopeMappingReferenceException.client(key.roleClientId()));
            this.scopeMappingApi.addClientRoles(
                    realm, clientScope, holderId, roleClientUuid, clientRoles(realm, key, roleClientUuid, roles));
        }
    }

    @Override
    public void removeRoles(final String realm, final ScopeMapping.Key key, final Set<String> roles) {
        Optional<String> holderId = holderIdOf(realm, key);
        if (holderId.isEmpty()) {
            return;
        }
        boolean clientScope = key.holderType() == HolderType.CLIENT_SCOPE;
        if (key.isRealmRoles()) {
            List<RoleRepresentation> existing = roles.stream()
                    .map(name -> this.roleApi.findRealmRole(realm, name))
                    .flatMap(Optional::stream)
                    .toList();
            if (!existing.isEmpty()) {
                this.scopeMappingApi.removeRealmRoles(realm, clientScope, holderId.get(), existing);
            }
            return;
        }
        Optional<String> roleClientUuid = this.roleApi.findClientUuid(realm, key.roleClientId());
        if (roleClientUuid.isEmpty()) {
            return;
        }
        List<RoleRepresentation> existing = roles.stream()
                .map(name -> this.roleApi.findClientRole(realm, roleClientUuid.get(), name))
                .flatMap(Optional::stream)
                .toList();
        if (!existing.isEmpty()) {
            this.scopeMappingApi.removeClientRoles(realm, clientScope, holderId.get(), roleClientUuid.get(), existing);
        }
    }

    private Optional<String> holderIdOf(final String realm, final ScopeMapping.Key key) {
        if (key.holderType() == HolderType.CLIENT) {
            return this.clientApi.findByClientId(realm, key.holder()).map(ClientRepresentation::getId);
        }
        return this.clientScopeApi.listClientScopes(realm).stream()
                .filter(scope -> Objects.equals(scope.getName(), key.holder()))
                .map(ClientScopeRepresentation::getId)
                .findFirst();
    }

    private static UnknownScopeMappingReferenceException unknownHolder(final ScopeMapping.Key key) {
        return key.holderType() == HolderType.CLIENT
                ? UnknownScopeMappingReferenceException.client(key.holder())
                : UnknownScopeMappingReferenceException.clientScope(key.holder());
    }

    private List<RoleRepresentation> realmRoles(final String realm, final Set<String> names) {
        return names.stream()
                .map(name -> this.roleApi
                        .findRealmRole(realm, name)
                        .orElseThrow(() -> UnknownScopeMappingReferenceException.realmRole(realm, name)))
                .toList();
    }

    private List<RoleRepresentation> clientRoles(
            final String realm, final ScopeMapping.Key key, final String roleClientUuid, final Set<String> names) {
        return names.stream()
                .map(name -> this.roleApi
                        .findClientRole(realm, roleClientUuid, name)
                        .orElseThrow(() ->
                                UnknownScopeMappingReferenceException.clientRole(realm, key.roleClientId(), name)))
                .toList();
    }
}
