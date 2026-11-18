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
import java.util.Set;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

/** Client scope operations of the Keycloak Admin API (ADR-003). Client scopes are addressed by their id. */
public interface ClientScopeApi {

    /** Client scopes, with their protocol mappers. */
    List<ClientScopeRepresentation> listClientScopes(String realm);

    /** Names of the default and optional client scopes of the realm. */
    Set<String> listRealmDefaultClientScopeNames(String realm);

    String createClientScope(String realm, ClientScopeRepresentation clientScope);

    void updateClientScope(String realm, String id, ClientScopeRepresentation clientScope);

    void deleteClientScope(String realm, String id);

    List<ProtocolMapperRepresentation> listProtocolMappers(String realm, String clientScopeId);

    void createProtocolMapper(String realm, String clientScopeId, ProtocolMapperRepresentation protocolMapper);

    void updateProtocolMapper(
            String realm, String clientScopeId, String protocolMapperId, ProtocolMapperRepresentation protocolMapper);

    void deleteProtocolMapper(String realm, String clientScopeId, String protocolMapperId);
}
