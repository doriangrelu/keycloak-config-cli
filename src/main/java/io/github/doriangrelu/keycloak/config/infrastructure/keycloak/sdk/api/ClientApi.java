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
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

/** Client operations of the Keycloak Admin API (ADR-003). Clients are addressed by their internal id. */
public interface ClientApi {

    /** Clients of the realm, with their protocol mappers and client scopes. */
    List<ClientRepresentation> listClients(String realm);

    Optional<ClientRepresentation> findByClientId(String realm, String clientId);

    String getSecret(String realm, String clientUuid);

    String createClient(String realm, ClientRepresentation client);

    void updateClient(String realm, String clientUuid, ClientRepresentation client);

    void deleteClient(String realm, String clientUuid);

    List<ProtocolMapperRepresentation> listProtocolMappers(String realm, String clientUuid);

    void createProtocolMapper(String realm, String clientUuid, ProtocolMapperRepresentation protocolMapper);

    void updateProtocolMapper(
            String realm, String clientUuid, String protocolMapperId, ProtocolMapperRepresentation protocolMapper);

    void deleteProtocolMapper(String realm, String clientUuid, String protocolMapperId);

    void addDefaultClientScope(String realm, String clientUuid, String clientScopeId);

    void removeDefaultClientScope(String realm, String clientUuid, String clientScopeId);

    void addOptionalClientScope(String realm, String clientUuid, String clientScopeId);

    void removeOptionalClientScope(String realm, String clientUuid, String clientScopeId);
}
