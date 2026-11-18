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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientScopeApi;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientScopeResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

/** {@link ClientScopeApi} on the Keycloak Admin client. */
public class ClientScopeAdapter extends AbstractAdapter implements ClientScopeApi {

    public ClientScopeAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<ClientScopeRepresentation> listClientScopes(final String realm) {
        return execute(() -> realm(realm).clientScopes().findAll());
    }

    @Override
    public Set<String> listRealmDefaultClientScopeNames(final String realm) {
        return execute(() -> {
            Set<String> names = new LinkedHashSet<>();
            realm(realm).getDefaultDefaultClientScopes().forEach(scope -> names.add(scope.getName()));
            realm(realm).getDefaultOptionalClientScopes().forEach(scope -> names.add(scope.getName()));
            return names;
        });
    }

    @Override
    public String createClientScope(final String realm, final ClientScopeRepresentation clientScope) {
        return createAndGetId(() -> realm(realm).clientScopes().create(clientScope));
    }

    @Override
    public void updateClientScope(final String realm, final String id, final ClientScopeRepresentation clientScope) {
        execute(() -> clientScope(realm, id).update(clientScope));
    }

    @Override
    public void deleteClientScope(final String realm, final String id) {
        execute(() -> clientScope(realm, id).remove());
    }

    @Override
    public List<ProtocolMapperRepresentation> listProtocolMappers(final String realm, final String clientScopeId) {
        return execute(
                () -> clientScope(realm, clientScopeId).getProtocolMappers().getMappers());
    }

    @Override
    public void createProtocolMapper(
            final String realm, final String clientScopeId, final ProtocolMapperRepresentation protocolMapper) {
        createAndGetId(
                () -> clientScope(realm, clientScopeId).getProtocolMappers().createMapper(protocolMapper));
    }

    @Override
    public void updateProtocolMapper(
            final String realm,
            final String clientScopeId,
            final String protocolMapperId,
            final ProtocolMapperRepresentation protocolMapper) {
        execute(() -> clientScope(realm, clientScopeId).getProtocolMappers().update(protocolMapperId, protocolMapper));
    }

    @Override
    public void deleteProtocolMapper(final String realm, final String clientScopeId, final String protocolMapperId) {
        execute(() -> clientScope(realm, clientScopeId).getProtocolMappers().delete(protocolMapperId));
    }

    private RealmResource realm(final String realm) {
        return keycloak().realm(realm);
    }

    private ClientScopeResource clientScope(final String realm, final String id) {
        return realm(realm).clientScopes().get(id);
    }
}
