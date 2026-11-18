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

package io.github.doriangrelu.keycloak.config.application.user.port;

import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import java.util.Optional;
import java.util.Set;

/** Users of a realm in Keycloak, with their role mappings and groups. */
public interface UserGateway {

    /**
     * The existing user for {@code desired}: the user with its username, else the user with its email, first name and
     * last name. Credentials are never read.
     */
    Optional<User> find(String realm, User desired);

    /** Creates the user with its credentials, but without role mappings nor groups. */
    void create(String realm, User user);

    /** Updates the user when it changed, then its role mappings and groups. */
    void update(String realm, User current, User target);

    /** Client ids of the clients created by Keycloak in the realm. */
    Set<String> keycloakClients(String realm);
}
