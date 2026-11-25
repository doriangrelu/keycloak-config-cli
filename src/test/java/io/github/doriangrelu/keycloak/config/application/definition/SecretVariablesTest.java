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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import org.junit.jupiter.api.Test;

class SecretVariablesTest {

    private static String proposed(final String clientId) {
        return SecretVariables.proposedFor(TemplatedValue.of(clientId)).raw();
    }

    @Test
    void theLiteralPartsAreCamelCased() {
        assertThat(proposed("my-api")).isEqualTo("{{myApiSecret}}");
        assertThat(proposed("frontend")).isEqualTo("{{frontendSecret}}");
        assertThat(proposed("my_api.v2")).isEqualTo("{{myApiV2Secret}}");
    }

    @Test
    void aPlaceholderIsNotPartOfTheName() {
        assertThat(proposed("{{env}}-api")).isEqualTo("{{apiSecret}}");
    }

    @Test
    void withoutLiteralPartTheNameIsGeneric() {
        assertThat(proposed("{{clientId}}")).isEqualTo("{{clientSecret}}");
    }

    @Test
    void theNameIsAlwaysAValidVariableName() {
        assertThat(proposed("3scale")).isEqualTo("{{client3scaleSecret}}");
    }
}
