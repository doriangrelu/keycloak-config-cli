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
import java.util.Map;
import java.util.Optional;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/** User operations of the Keycloak Admin API (ADR-003). Users are addressed by their id. */
public interface UserApi {

    /** The user with exactly this username, in its full representation. */
    Optional<UserRepresentation> findByUsername(String realm, String username);

    /** Users whose email, first name and last name contain the given values; {@code null} values are not searched. */
    List<UserRepresentation> search(String realm, String email, String firstName, String lastName);

    /** The user in its full representation. */
    UserRepresentation getUser(String realm, String userId);

    String createUser(String realm, UserRepresentation user);

    void updateUser(String realm, String userId, UserRepresentation user);

    List<RoleRepresentation> listRealmRoles(String realm, String userId);

    /** Client role mappings of the user, by client id. */
    Map<String, List<RoleRepresentation>> listClientRoles(String realm, String userId);

    void addRealmRoles(String realm, String userId, List<RoleRepresentation> roles);

    void removeRealmRoles(String realm, String userId, List<RoleRepresentation> roles);

    void addClientRoles(String realm, String userId, String clientUuid, List<RoleRepresentation> roles);

    void removeClientRoles(String realm, String userId, String clientUuid, List<RoleRepresentation> roles);

    List<GroupRepresentation> listGroups(String realm, String userId);

    void joinGroup(String realm, String userId, String groupId);

    void leaveGroup(String realm, String userId, String groupId);
}
