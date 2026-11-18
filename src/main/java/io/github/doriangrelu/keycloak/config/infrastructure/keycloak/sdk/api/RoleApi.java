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
import org.keycloak.representations.idm.RoleRepresentation;

/** Role operations of the Keycloak Admin API (ADR-003). Clients are addressed by their internal id. */
public interface RoleApi {

    /** Realm roles, with their attributes. */
    List<RoleRepresentation> listRealmRoles(String realm);

    void createRealmRole(String realm, RoleRepresentation role);

    void updateRealmRole(String realm, String name, RoleRepresentation role);

    void deleteRealmRole(String realm, String name);

    /** Internal id of a client, from its client id. */
    Optional<String> findClientUuid(String realm, String clientId);

    /** Roles of a client, with their attributes. */
    List<RoleRepresentation> listClientRoles(String realm, String clientUuid);

    void createClientRole(String realm, String clientUuid, RoleRepresentation role);

    void updateClientRole(String realm, String clientUuid, String name, RoleRepresentation role);

    void deleteClientRole(String realm, String clientUuid, String name);
}
