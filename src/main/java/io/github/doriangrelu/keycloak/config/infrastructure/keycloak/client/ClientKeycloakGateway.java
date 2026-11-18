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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client;

import io.github.doriangrelu.keycloak.config.application.client.ClientRejectedException;
import io.github.doriangrelu.keycloak.config.application.client.port.ClientGateway;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientScopeApi;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link ClientGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ClientKeycloakGateway implements ClientGateway {

    private static final Logger logger = LoggerFactory.getLogger(ClientKeycloakGateway.class);

    /**
     * Failures reported as a refused client, as the legacy import did for any error answered by Keycloak (a value too
     * long for the database is answered with 500); unavailability and timeouts are reported as such.
     */
    private static final Set<KeycloakFailure> REFUSALS = Set.of(
            KeycloakFailure.REJECTED, KeycloakFailure.NOT_FOUND, KeycloakFailure.CONFLICT, KeycloakFailure.UNEXPECTED);

    private final ClientApi clientApi;
    private final ClientScopeApi clientScopeApi;
    private final ClientKeycloakMapper mapper;

    public ClientKeycloakGateway(
            final ClientApi clientApi, final ClientScopeApi clientScopeApi, final ClientKeycloakMapper mapper) {
        this.clientApi = clientApi;
        this.clientScopeApi = clientScopeApi;
        this.mapper = mapper;
    }

    /** Missing attributes, scopes and mappers read as empty, so that they compare equal to an import declaring none. */
    @Override
    public List<Client> findAll(final String realm, final Set<String> withSecretOf) {
        return this.clientApi.listClients(realm).stream()
                .map(client -> {
                    if (withSecretOf.contains(client.getClientId()) && !Boolean.TRUE.equals(client.isPublicClient())) {
                        client.setSecret(this.clientApi.getSecret(realm, client.getId()));
                    }
                    if (client.getAttributes() == null) {
                        client.setAttributes(Map.of());
                    }
                    if (client.getDefaultClientScopes() == null) {
                        client.setDefaultClientScopes(List.of());
                    }
                    if (client.getOptionalClientScopes() == null) {
                        client.setOptionalClientScopes(List.of());
                    }
                    if (client.getProtocolMappers() == null) {
                        client.setProtocolMappers(List.of());
                    }
                    return this.mapper.toClient(client);
                })
                .toList();
    }

    @Override
    public void create(final String realm, final Client client) {
        try {
            this.clientApi.createClient(realm, this.mapper.toRepresentation(client));
        } catch (final KeycloakAccessException error) {
            throw refused(error, () -> ClientRejectedException.onCreate(client.clientId(), realm, error));
        }
    }

    @Override
    public void update(final String realm, final Client current, final Client target) {
        String clientUuid = uuidOf(realm, target.clientId());
        boolean mappersChanged = target.protocolMappers() != null
                && !Objects.equals(current.protocolMappers(), target.protocolMappers());
        if (mappersChanged || !Objects.equals(current.withoutMappersAndScopes(), target.withoutMappersAndScopes())) {
            updateClient(realm, clientUuid, target, mappersChanged);
        }
        updateScopes(realm, clientUuid, current, target);
    }

    @Override
    public void delete(final String realm, final String clientId) {
        this.clientApi.deleteClient(realm, uuidOf(realm, clientId));
    }

    /**
     * Protocol mappers travel with the client, as in the legacy import: Keycloak adds and removes them itself, and
     * ignores a mapper of an unknown type.
     */
    private void updateClient(
            final String realm, final String clientUuid, final Client target, final boolean withMappers) {
        ClientRepresentation representation = this.mapper.toRepresentation(target);
        representation.setId(clientUuid);
        representation.setDefaultClientScopes(null);
        representation.setOptionalClientScopes(null);
        if (!withMappers) {
            representation.setProtocolMappers(null);
        }
        try {
            this.clientApi.updateClient(realm, clientUuid, representation);
        } catch (final KeycloakAccessException error) {
            // With fine-grained admin permissions v2, Keycloak manages the admin-permissions client itself
            if (Client.ADMIN_PERMISSIONS.equals(target.clientId()) && error.getFailure() == KeycloakFailure.REJECTED) {
                logger.info(
                        "Skipping update for 'admin-permissions' client in realm '{}' - managed by Keycloak", realm);
                return;
            }
            throw refused(error, () -> ClientRejectedException.onUpdate(target.identifier(), realm, error));
        }
    }

    /**
     * Updates the default and optional client scopes: every removal first, then every addition, since a scope moving
     * from optional to default must leave the client before it can come back. Scopes missing from the realm are
     * ignored, as the legacy import did.
     */
    private void updateScopes(final String realm, final String clientUuid, final Client current, final Client target) {
        boolean defaultChanged = changed(current.defaultClientScopes(), target.defaultClientScopes());
        boolean optionalChanged = changed(current.optionalClientScopes(), target.optionalClientScopes());
        if (!defaultChanged && !optionalChanged) {
            return;
        }
        Map<String, String> idsByName = this.clientScopeApi.listClientScopes(realm).stream()
                .collect(Collectors.toMap(
                        ClientScopeRepresentation::getName,
                        ClientScopeRepresentation::getId,
                        (first, second) -> first));
        if (defaultChanged) {
            apply(
                    minus(current.defaultClientScopes(), target.defaultClientScopes()),
                    idsByName,
                    (name, id) -> this.clientApi.removeDefaultClientScope(realm, clientUuid, id));
        }
        if (optionalChanged) {
            apply(
                    minus(current.optionalClientScopes(), target.optionalClientScopes()),
                    idsByName,
                    (name, id) -> this.clientApi.removeOptionalClientScope(realm, clientUuid, id));
        }
        if (defaultChanged) {
            apply(
                    minus(target.defaultClientScopes(), current.defaultClientScopes()),
                    idsByName,
                    (name, id) -> this.clientApi.addDefaultClientScope(realm, clientUuid, id));
        }
        if (optionalChanged) {
            apply(
                    minus(target.optionalClientScopes(), current.optionalClientScopes()),
                    idsByName,
                    (name, id) -> this.clientApi.addOptionalClientScope(realm, clientUuid, id));
        }
    }

    private static boolean changed(final Set<String> current, final Set<String> target) {
        return target != null && !Objects.equals(current, target);
    }

    private static Set<String> minus(final Set<String> values, final Set<String> removed) {
        Set<String> result = new LinkedHashSet<>(values == null ? Set.of() : values);
        result.removeAll(removed == null ? Set.of() : removed);
        return result;
    }

    private static void apply(
            final Set<String> names, final Map<String, String> idsByName, final BiConsumer<String, String> action) {
        names.stream().filter(idsByName::containsKey).forEach(name -> action.accept(name, idsByName.get(name)));
    }

    private String uuidOf(final String realm, final String clientId) {
        return this.clientApi
                .findByClientId(realm, clientId)
                .map(ClientRepresentation::getId)
                .orElseThrow(() -> new IllegalStateException("Client '" + clientId + "' not found in realm " + realm));
    }

    private static RuntimeException refused(
            final KeycloakAccessException error, final Supplier<RuntimeException> translation) {
        return REFUSALS.contains(error.getFailure()) ? translation.get() : error;
    }
}
