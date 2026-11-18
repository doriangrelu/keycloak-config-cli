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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

/** {@link ClientApi} on the Keycloak Admin client. */
public class ClientAdapter extends AbstractAdapter implements ClientApi {

    public ClientAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<ClientRepresentation> listClients(final String realm) {
        return execute(() -> clients(realm).findAll());
    }

    @Override
    public Optional<ClientRepresentation> findByClientId(final String realm, final String clientId) {
        return execute(() -> clients(realm).findByClientId(clientId).stream().findFirst());
    }

    @Override
    public String getSecret(final String realm, final String clientUuid) {
        return execute(() -> client(realm, clientUuid).getSecret().getValue());
    }

    @Override
    public String createClient(final String realm, final ClientRepresentation client) {
        return createAndGetId(() -> clients(realm).create(client));
    }

    @Override
    public void updateClient(final String realm, final String clientUuid, final ClientRepresentation client) {
        execute(() -> client(realm, clientUuid).update(client));
    }

    @Override
    public void deleteClient(final String realm, final String clientUuid) {
        execute(() -> client(realm, clientUuid).remove());
    }

    @Override
    public List<ProtocolMapperRepresentation> listProtocolMappers(final String realm, final String clientUuid) {
        return execute(() -> client(realm, clientUuid).getProtocolMappers().getMappers());
    }

    @Override
    public void createProtocolMapper(
            final String realm, final String clientUuid, final ProtocolMapperRepresentation protocolMapper) {
        createAndGetId(() -> client(realm, clientUuid).getProtocolMappers().createMapper(protocolMapper));
    }

    @Override
    public void updateProtocolMapper(
            final String realm,
            final String clientUuid,
            final String protocolMapperId,
            final ProtocolMapperRepresentation protocolMapper) {
        execute(() -> client(realm, clientUuid).getProtocolMappers().update(protocolMapperId, protocolMapper));
    }

    @Override
    public void deleteProtocolMapper(final String realm, final String clientUuid, final String protocolMapperId) {
        execute(() -> client(realm, clientUuid).getProtocolMappers().delete(protocolMapperId));
    }

    @Override
    public void addDefaultClientScope(final String realm, final String clientUuid, final String clientScopeId) {
        execute(() -> client(realm, clientUuid).addDefaultClientScope(clientScopeId));
    }

    @Override
    public void removeDefaultClientScope(final String realm, final String clientUuid, final String clientScopeId) {
        execute(() -> client(realm, clientUuid).removeDefaultClientScope(clientScopeId));
    }

    @Override
    public void addOptionalClientScope(final String realm, final String clientUuid, final String clientScopeId) {
        execute(() -> client(realm, clientUuid).addOptionalClientScope(clientScopeId));
    }

    @Override
    public void removeOptionalClientScope(final String realm, final String clientUuid, final String clientScopeId) {
        execute(() -> client(realm, clientUuid).removeOptionalClientScope(clientScopeId));
    }

    private ClientsResource clients(final String realm) {
        return keycloak().realm(realm).clients();
    }

    private ClientResource client(final String realm, final String clientUuid) {
        return clients(realm).get(clientUuid);
    }
}
