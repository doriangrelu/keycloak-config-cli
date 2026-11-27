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

import io.github.doriangrelu.keycloak.config.domain.definition.SecretVariableNames;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;

/** The variable proposed for the secret of a client. */
final class SecretVariables {

    private SecretVariables() {}

    /**
     * The literal parts of the id, split on every character that is not alphanumeric, in camel case and followed by
     * {@code Secret}: {@code my-api} gives {@code {{myApiSecret}}}, {@code {{env}}-api} gives {@code {{apiSecret}}}.
     */
    static TemplatedValue proposedFor(final TemplatedValue clientId) {
        return TemplatedValue.of("{{" + SecretVariableNames.client(clientId.raw()) + "}}");
    }
}
