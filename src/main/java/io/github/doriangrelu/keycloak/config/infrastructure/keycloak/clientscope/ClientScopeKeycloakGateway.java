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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope;

import io.github.doriangrelu.keycloak.config.application.clientscope.ProtocolMapperRejectedException;
import io.github.doriangrelu.keycloak.config.application.clientscope.port.ClientScopeGateway;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ProtocolMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientScopeApi;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link ClientScopeGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ClientScopeKeycloakGateway implements ClientScopeGateway {

    private static final Set<KeycloakFailure> REFUSALS =
            Set.of(KeycloakFailure.REJECTED, KeycloakFailure.NOT_FOUND, KeycloakFailure.CONFLICT);

    private final ClientScopeApi clientScopeApi;
    private final ClientScopeKeycloakMapper mapper;

    public ClientScopeKeycloakGateway(final ClientScopeApi clientScopeApi, final ClientScopeKeycloakMapper mapper) {
        this.clientScopeApi = clientScopeApi;
        this.mapper = mapper;
    }

    /** Missing attributes and mappers read as empty, so that they compare equal to an import declaring none. */
    @Override
    public List<ClientScope> findAll(final String realm) {
        Set<String> realmDefaults = this.clientScopeApi.listRealmDefaultClientScopeNames(realm);
        return this.clientScopeApi.listClientScopes(realm).stream()
                .map(scope -> {
                    if (scope.getAttributes() == null) {
                        scope.setAttributes(Map.of());
                    }
                    if (scope.getProtocolMappers() == null) {
                        scope.setProtocolMappers(List.of());
                    }
                    return this.mapper.toClientScope(scope, realmDefaults.contains(scope.getName()));
                })
                .toList();
    }

    @Override
    public void create(final String realm, final ClientScope clientScope) {
        this.clientScopeApi.createClientScope(realm, this.mapper.toRepresentation(clientScope));
    }

    @Override
    public void update(final String realm, final ClientScope current, final ClientScope target) {
        String id = idOf(realm, target.name());
        if (!Objects.equals(current.withoutProtocolMappers(), target.withoutProtocolMappers())) {
            this.clientScopeApi.updateClientScope(
                    realm, id, this.mapper.toRepresentation(target.withoutProtocolMappers()));
        }
        if (target.protocolMappers() != null && !Objects.equals(current.protocolMappers(), target.protocolMappers())) {
            updateProtocolMappers(realm, id, target);
        }
    }

    @Override
    public void delete(final String realm, final String name) {
        this.clientScopeApi.deleteClientScope(realm, idOf(realm, name));
    }

    private void updateProtocolMappers(final String realm, final String clientScopeId, final ClientScope target) {
        Map<String, ProtocolMapperRepresentation> existing =
                this.clientScopeApi.listProtocolMappers(realm, clientScopeId).stream()
                        .collect(Collectors.toMap(
                                ProtocolMapperRepresentation::getName, Function.identity(), (first, second) -> first));
        Set<String> wanted =
                target.protocolMappers().stream().map(ProtocolMapper::name).collect(Collectors.toSet());

        existing.values().stream()
                .filter(mapper -> !wanted.contains(mapper.getName()))
                .forEach(mapper -> this.clientScopeApi.deleteProtocolMapper(realm, clientScopeId, mapper.getId()));

        for (ProtocolMapper mapper : target.protocolMappers()) {
            ProtocolMapperRepresentation current = existing.get(mapper.name());
            ProtocolMapperRepresentation representation = this.mapper.toRepresentation(mapper);
            if (current == null) {
                this.clientScopeApi.createProtocolMapper(realm, clientScopeId, representation);
            } else if (!this.mapper.toProtocolMapper(current).equals(mapper)) {
                representation.setId(current.getId());
                updateProtocolMapper(realm, clientScopeId, target.name(), current.getId(), representation);
            }
        }
    }

    private void updateProtocolMapper(
            final String realm,
            final String clientScopeId,
            final String clientScope,
            final String protocolMapperId,
            final ProtocolMapperRepresentation representation) {
        try {
            this.clientScopeApi.updateProtocolMapper(realm, clientScopeId, protocolMapperId, representation);
        } catch (final KeycloakAccessException error) {
            // An unknown mapper type is answered with 404: every refusal of the mapper is a configuration error
            if (!REFUSALS.contains(error.getFailure())) {
                throw error;
            }
            throw new ProtocolMapperRejectedException(realm, clientScope, representation.getName(), error);
        }
    }

    private String idOf(final String realm, final String name) {
        return this.clientScopeApi.listClientScopes(realm).stream()
                .filter(scope -> name.equals(scope.getName()))
                .map(ClientScopeRepresentation::getId)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Client scope '" + name + "' not found in realm " + realm));
    }
}
