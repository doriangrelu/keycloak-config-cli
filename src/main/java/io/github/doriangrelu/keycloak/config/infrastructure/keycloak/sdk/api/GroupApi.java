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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api;

import java.util.List;
import java.util.Optional;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

/** Group operations of the Keycloak Admin API (ADR-003). Groups are addressed by their id. */
public interface GroupApi {

    List<GroupRepresentation> listTopLevelGroups(String realm);

    List<GroupRepresentation> listSubGroups(String realm, String groupId);

    /** The group with its attributes and role mappings. */
    GroupRepresentation getGroup(String realm, String groupId);

    Optional<GroupRepresentation> findByPath(String realm, String path);

    String createTopLevelGroup(String realm, GroupRepresentation group);

    String createSubGroup(String realm, String parentId, GroupRepresentation group);

    void updateGroup(String realm, String groupId, GroupRepresentation group);

    void deleteGroup(String realm, String groupId);

    void addRealmRoles(String realm, String groupId, List<RoleRepresentation> roles);

    void removeRealmRoles(String realm, String groupId, List<RoleRepresentation> roles);

    void addClientRoles(String realm, String groupId, String clientUuid, List<RoleRepresentation> roles);

    void removeClientRoles(String realm, String groupId, String clientUuid, List<RoleRepresentation> roles);
}
