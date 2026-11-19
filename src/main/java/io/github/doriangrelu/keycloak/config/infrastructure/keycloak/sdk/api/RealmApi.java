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
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

/** Realm operations of the Keycloak Admin API (ADR-003). */
public interface RealmApi {

    Optional<RealmRepresentation> findRealm(String realm);

    void createRealm(RealmRepresentation realm);

    void updateRealm(String realm, RealmRepresentation representation);

    /** @param optional the optional default client scopes instead of the default ones */
    List<ClientScopeRepresentation> listDefaultClientScopes(String realm, boolean optional);

    void addDefaultClientScope(String realm, boolean optional, String clientScopeId);

    void removeDefaultClientScope(String realm, boolean optional, String clientScopeId);

    void addDefaultGroup(String realm, String groupId);

    void removeDefaultGroup(String realm, String groupId);
}
