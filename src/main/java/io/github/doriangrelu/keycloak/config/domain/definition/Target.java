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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.time.Duration;
import java.util.Optional;

/**
 * A named Keycloak server the shell can connect to. It never holds a secret: the password or the client secret is
 * asked for when the connection is opened.
 *
 * @param name the name given in {@code kcc-targets.yaml}
 * @param url the base URL of Keycloak
 * @param loginRealm the realm the user or the client logs in to
 * @param clientId the client used to log in
 * @param grantType how the shell authenticates
 * @param user the user to log in with, for the {@code password} grant
 * @param sslVerify whether the certificate of the server is verified
 * @param connectTimeout how long to wait for the server before giving up
 */
public record Target(
        String name,
        String url,
        String loginRealm,
        String clientId,
        GrantType grantType,
        Optional<String> user,
        boolean sslVerify,
        Duration connectTimeout) {}
