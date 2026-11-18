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
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;

/**
 * Scope mapping operations of the Keycloak Admin API (ADR-003). A holder is a client or a client scope, addressed by
 * its id.
 */
public interface ScopeMappingApi {

    /** Scope mappings of realm roles, from a partial export of the realm. */
    List<ScopeMappingRepresentation> listRealmRoleScopeMappings(String realm);

    /** Scope mappings of client roles by client id of the client owning the roles, from a partial export. */
    Map<String, List<ScopeMappingRepresentation>> listClientRoleScopeMappings(String realm);

    void addRealmRoles(String realm, boolean clientScope, String holderId, List<RoleRepresentation> roles);

    void removeRealmRoles(String realm, boolean clientScope, String holderId, List<RoleRepresentation> roles);

    void addClientRoles(
            String realm, boolean clientScope, String holderId, String roleClientUuid, List<RoleRepresentation> roles);

    void removeClientRoles(
            String realm, boolean clientScope, String holderId, String roleClientUuid, List<RoleRepresentation> roles);
}
