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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/** {@link UserApi} on the Keycloak Admin client. */
public class UserAdapter extends AbstractAdapter implements UserApi {

    private static final int SEARCH_LIMIT = 100;

    public UserAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public Optional<UserRepresentation> findByUsername(final String realm, final String username) {
        List<UserRepresentation> found =
                execute(() -> keycloak().realm(realm).users().search(username, true));
        return found.stream().findFirst().map(user -> getUser(realm, user.getId()));
    }

    @Override
    public List<UserRepresentation> search(
            final String realm, final String email, final String firstName, final String lastName) {
        return execute(() -> keycloak()
                .realm(realm)
                .users()
                .search("", firstName, lastName, email, null, null, null, 0, SEARCH_LIMIT, null, null));
    }

    @Override
    public UserRepresentation getUser(final String realm, final String userId) {
        return execute(() -> user(realm, userId).toRepresentation());
    }

    @Override
    public String createUser(final String realm, final UserRepresentation user) {
        return createAndGetId(() -> keycloak().realm(realm).users().create(user));
    }

    @Override
    public void updateUser(final String realm, final String userId, final UserRepresentation user) {
        execute(() -> user(realm, userId).update(user));
    }

    @Override
    public List<RoleRepresentation> listRealmRoles(final String realm, final String userId) {
        return execute(() -> user(realm, userId).roles().realmLevel().listAll());
    }

    @Override
    public Map<String, List<RoleRepresentation>> listClientRoles(final String realm, final String userId) {
        MappingsRepresentation mappings =
                execute(() -> user(realm, userId).roles().getAll());
        Map<String, List<RoleRepresentation>> roles = new LinkedHashMap<>();
        if (mappings.getClientMappings() != null) {
            for (Map.Entry<String, ClientMappingsRepresentation> client :
                    mappings.getClientMappings().entrySet()) {
                List<RoleRepresentation> clientRoles = client.getValue().getMappings();
                roles.put(client.getKey(), clientRoles == null ? List.of() : clientRoles);
            }
        }
        return roles;
    }

    @Override
    public void addRealmRoles(final String realm, final String userId, final List<RoleRepresentation> roles) {
        execute(() -> user(realm, userId).roles().realmLevel().add(roles));
    }

    @Override
    public void removeRealmRoles(final String realm, final String userId, final List<RoleRepresentation> roles) {
        execute(() -> user(realm, userId).roles().realmLevel().remove(roles));
    }

    @Override
    public void addClientRoles(
            final String realm, final String userId, final String clientUuid, final List<RoleRepresentation> roles) {
        execute(() -> user(realm, userId).roles().clientLevel(clientUuid).add(roles));
    }

    @Override
    public void removeClientRoles(
            final String realm, final String userId, final String clientUuid, final List<RoleRepresentation> roles) {
        execute(() -> user(realm, userId).roles().clientLevel(clientUuid).remove(roles));
    }

    @Override
    public List<GroupRepresentation> listGroups(final String realm, final String userId) {
        return execute(() -> user(realm, userId).groups());
    }

    @Override
    public void joinGroup(final String realm, final String userId, final String groupId) {
        execute(() -> user(realm, userId).joinGroup(groupId));
    }

    @Override
    public void leaveGroup(final String realm, final String userId, final String groupId) {
        execute(() -> user(realm, userId).leaveGroup(groupId));
    }

    private UserResource user(final String realm, final String userId) {
        return keycloak().realm(realm).users().get(userId);
    }
}
