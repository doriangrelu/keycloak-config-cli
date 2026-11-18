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

package io.github.doriangrelu.keycloak.config.application.clientscope.port;

import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import java.util.List;

/** Access to the client scopes of a realm in Keycloak. */
public interface ClientScopeGateway {

    /** Client scopes, with their protocol mappers and whether they are default or optional scopes of the realm. */
    List<ClientScope> findAll(String realm);

    void create(String realm, ClientScope clientScope);

    /**
     * Updates the client scope and, when {@code target} specifies them, its protocol mappers.
     *
     * @throws io.github.doriangrelu.keycloak.config.application.clientscope.ProtocolMapperRejectedException when
     *     Keycloak refuses a protocol mapper
     */
    void update(String realm, ClientScope current, ClientScope target);

    void delete(String realm, String name);
}
