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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

/** {@link RoleApi} on the Keycloak Admin client. */
public class RoleAdapter extends AbstractAdapter implements RoleApi {

    /** Full representations, attributes included. */
    private static final boolean FULL_REPRESENTATION = false;

    public RoleAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<RoleRepresentation> listRealmRoles(final String realm) {
        return execute(() -> realmRoles(realm).list(FULL_REPRESENTATION));
    }

    @Override
    public void createRealmRole(final String realm, final RoleRepresentation role) {
        execute(() -> realmRoles(realm).create(role));
    }

    @Override
    public void updateRealmRole(final String realm, final String name, final RoleRepresentation role) {
        execute(() -> realmRoles(realm).get(name).update(role));
    }

    @Override
    public void deleteRealmRole(final String realm, final String name) {
        execute(() -> realmRoles(realm).deleteRole(name));
    }

    @Override
    public Optional<String> findClientUuid(final String realm, final String clientId) {
        return execute(() -> keycloak().realm(realm).clients().findByClientId(clientId).stream()
                .findFirst()
                .map(ClientRepresentation::getId));
    }

    @Override
    public List<RoleRepresentation> listClientRoles(final String realm, final String clientUuid) {
        return execute(() -> clientRoles(realm, clientUuid).list(FULL_REPRESENTATION));
    }

    @Override
    public void createClientRole(final String realm, final String clientUuid, final RoleRepresentation role) {
        execute(() -> clientRoles(realm, clientUuid).create(role));
    }

    @Override
    public void updateClientRole(
            final String realm, final String clientUuid, final String name, final RoleRepresentation role) {
        execute(() -> clientRoles(realm, clientUuid).get(name).update(role));
    }

    @Override
    public void deleteClientRole(final String realm, final String clientUuid, final String name) {
        execute(() -> clientRoles(realm, clientUuid).deleteRole(name));
    }

    private RolesResource realmRoles(final String realm) {
        return keycloak().realm(realm).roles();
    }

    private RolesResource clientRoles(final String realm, final String clientUuid) {
        return keycloak().realm(realm).clients().get(clientUuid).roles();
    }
}
