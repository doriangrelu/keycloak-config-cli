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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

/** {@link GroupApi} on the Keycloak Admin client. */
public class GroupAdapter extends AbstractAdapter implements GroupApi {

    /** Full representations, attributes included. */
    private static final boolean FULL_REPRESENTATION = false;

    public GroupAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<GroupRepresentation> listTopLevelGroups(final String realm) {
        return execute(() -> keycloak().realm(realm).groups().groups(null, 0, Integer.MAX_VALUE, FULL_REPRESENTATION));
    }

    @Override
    public List<GroupRepresentation> listSubGroups(final String realm, final String groupId) {
        return execute(() -> group(realm, groupId).getSubGroups(0, Integer.MAX_VALUE, FULL_REPRESENTATION));
    }

    @Override
    public GroupRepresentation getGroup(final String realm, final String groupId) {
        return execute(() -> group(realm, groupId).toRepresentation());
    }

    @Override
    public Optional<GroupRepresentation> findByPath(final String realm, final String path) {
        return find(() -> keycloak().realm(realm).getGroupByPath(path));
    }

    @Override
    public String createTopLevelGroup(final String realm, final GroupRepresentation group) {
        return createAndGetId(() -> keycloak().realm(realm).groups().add(group));
    }

    @Override
    public String createSubGroup(final String realm, final String parentId, final GroupRepresentation group) {
        return createAndGetId(() -> group(realm, parentId).subGroup(group));
    }

    @Override
    public void updateGroup(final String realm, final String groupId, final GroupRepresentation group) {
        execute(() -> group(realm, groupId).update(group));
    }

    @Override
    public void deleteGroup(final String realm, final String groupId) {
        execute(() -> group(realm, groupId).remove());
    }

    @Override
    public void addRealmRoles(final String realm, final String groupId, final List<RoleRepresentation> roles) {
        execute(() -> group(realm, groupId).roles().realmLevel().add(roles));
    }

    @Override
    public void removeRealmRoles(final String realm, final String groupId, final List<RoleRepresentation> roles) {
        execute(() -> group(realm, groupId).roles().realmLevel().remove(roles));
    }

    @Override
    public void addClientRoles(
            final String realm, final String groupId, final String clientUuid, final List<RoleRepresentation> roles) {
        execute(() -> group(realm, groupId).roles().clientLevel(clientUuid).add(roles));
    }

    @Override
    public void removeClientRoles(
            final String realm, final String groupId, final String clientUuid, final List<RoleRepresentation> roles) {
        execute(() -> group(realm, groupId).roles().clientLevel(clientUuid).remove(roles));
    }

    private GroupResource group(final String realm, final String groupId) {
        return keycloak().realm(realm).groups().group(groupId);
    }
}
