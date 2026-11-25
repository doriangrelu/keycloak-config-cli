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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The answers already given to the questions of a client, such as by the options of a command: a given answer is not
 * asked.
 *
 * @param url the URL of the application, which only serves to propose the redirect URIs and the web origins
 * @param declareType whether writing the first client of the realm is already confirmed
 */
public record ClientAnswers(
        Optional<TemplatedValue> clientId,
        Optional<TemplatedValue> name,
        Optional<TemplatedValue> description,
        Optional<ApplicationType> type,
        Optional<TemplatedValue> url,
        Optional<List<TemplatedValue>> redirectUris,
        Optional<List<TemplatedValue>> webOrigins,
        Optional<SecretAnswer> secret,
        boolean declareType) {

    public ClientAnswers {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(redirectUris, "redirectUris");
        Objects.requireNonNull(webOrigins, "webOrigins");
        Objects.requireNonNull(secret, "secret");
    }

    /** Nothing is given: every question is asked. */
    public static ClientAnswers none() {
        return new ClientAnswers(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false);
    }
}
