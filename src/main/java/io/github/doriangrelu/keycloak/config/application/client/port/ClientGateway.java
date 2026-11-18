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

package io.github.doriangrelu.keycloak.config.application.client.port;

import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import java.util.List;
import java.util.Set;

/** Access to the clients of a realm in Keycloak. */
public interface ClientGateway {

    /**
     * Clients of the realm, with their protocol mappers and client scopes.
     *
     * @param withSecretOf client ids whose secret is needed: secrets are read one by one, only when compared
     */
    List<Client> findAll(String realm, Set<String> withSecretOf);

    /** @throws io.github.doriangrelu.keycloak.config.application.client.ClientRejectedException when refused */
    void create(String realm, Client client);

    /**
     * Updates the client, then its protocol mappers and client scopes when {@code target} specifies them.
     *
     * @throws io.github.doriangrelu.keycloak.config.application.client.ClientRejectedException when refused
     */
    void update(String realm, Client current, Client target);

    void delete(String realm, String clientId);
}
