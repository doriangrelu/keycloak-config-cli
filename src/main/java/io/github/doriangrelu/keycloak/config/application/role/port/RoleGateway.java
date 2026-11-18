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

package io.github.doriangrelu.keycloak.config.application.role.port;

import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.List;
import java.util.Optional;

/** Access to the roles of a realm in Keycloak. */
public interface RoleGateway {

    List<Role> findRealmRoles(String realm);

    /** @return the roles of the client, or empty when the client does not exist */
    Optional<List<Role>> findClientRoles(String realm, String clientId);

    /** @throws io.github.doriangrelu.keycloak.config.application.role.UnknownClientException for a missing client */
    void create(String realm, Role role);

    void update(String realm, Role role);

    void delete(String realm, RoleKey key);
}
