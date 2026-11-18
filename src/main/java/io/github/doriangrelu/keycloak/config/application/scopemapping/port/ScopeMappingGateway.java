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

package io.github.doriangrelu.keycloak.config.application.scopemapping.port;

import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import java.util.List;
import java.util.Set;

/** Scope mappings of the clients and client scopes of a realm in Keycloak. */
public interface ScopeMappingGateway {

    /**
     * Scope mappings holding at least one role.
     *
     * @param clientRoles whether to read the mappings of client roles, or those of realm roles
     */
    List<ScopeMapping> findAll(String realm, boolean clientRoles);

    void addRoles(String realm, ScopeMapping.Key key, Set<String> roles);

    /** Removes the roles; does nothing when the holder or the client owning the roles no longer exists. */
    void removeRoles(String realm, ScopeMapping.Key key, Set<String> roles);
}
