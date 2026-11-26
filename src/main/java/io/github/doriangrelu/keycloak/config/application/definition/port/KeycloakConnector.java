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

package io.github.doriangrelu.keycloak.config.application.definition.port;

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;

/** Opens a connection to the Keycloak of a target (ADR-010). */
public interface KeycloakConnector {

    /**
     * Opens a connection and checks it with a first call, which reads the version of Keycloak.
     *
     * @param secret the password of the user, or the secret of the client, depending on the grant type of the target
     * @throws io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException when Keycloak
     *     cannot be reached or does not answer in time
     * @throws io.github.doriangrelu.keycloak.config.application.definition.KeycloakAuthenticationException when
     *     Keycloak refuses the login
     */
    Connection connect(Target target, String secret);
}
