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

package io.github.doriangrelu.keycloak.config.domain.authenticationflow.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthenticationFlowTest {

    private static UnmodeledAttributes definition(String description, String requirement) {
        return new UnmodeledAttributes(Map.of(
                "description",
                description,
                "authenticationExecutions",
                List.of(Map.of("authenticator", "auth-cookie", "requirement", requirement))));
    }

    private static AuthenticationFlow flow(String id, boolean builtIn, UnmodeledAttributes definition) {
        AuthenticatorConfig config = new AuthenticatorConfig("config", Map.of("key", "value"), false);
        return new AuthenticationFlow("browser", id, builtIn, definition, Map.of(), Map.of("config", config));
    }

    @Test
    void anUnchangedTreeKeepsTheCurrentFlowWhateverItsConfigs() {
        AuthenticationFlow current = new AuthenticationFlow(
                "browser", "kc-id", false, definition("Browser", "REQUIRED"), Map.of(), Map.of());

        assertThat(current.mergedWith(flow(null, false, definition("Browser", "REQUIRED")), "realm"))
                .isSameAs(current);
    }

    @Test
    void aChangedTreeGivesTheDesiredFlowWithTheCurrentId() {
        AuthenticationFlow current = flow("kc-id", false, definition("Browser", "REQUIRED"));

        AuthenticationFlow merged =
                current.mergedWith(flow("declared-id", false, definition("Browser", "ALTERNATIVE")), "realm");

        assertThat(merged.id()).isEqualTo("kc-id");
        assertThat(merged.definition()).isEqualTo(definition("Browser", "ALTERNATIVE"));
        assertThat(merged.authenticatorConfigs()).containsKey("config");
    }

    @Test
    void aBuiltInFlowIsPatchedWithTheDeclaredFields() {
        AuthenticationFlow current = flow("kc-id", true, definition("Browser", "REQUIRED"));
        UnmodeledAttributes declared = new UnmodeledAttributes(Map.of("description", "Changed"));

        AuthenticationFlow merged = current.mergedWith(flow(null, true, declared), "realm");

        assertThat(merged.definition().values())
                .containsEntry("description", "Changed")
                .containsKey("authenticationExecutions");
    }

    @Test
    void theBuiltInFlagCannotChange() {
        AuthenticationFlow custom = flow("id", false, definition("Browser", "REQUIRED"));
        AuthenticationFlow builtIn = flow("id", true, definition("Browser", "REQUIRED"));

        assertThatThrownBy(() -> custom.mergedWith(builtIn, "realm"))
                .isInstanceOf(InvalidAuthenticationFlowException.class)
                .hasMessage("Unable to update flow 'browser' in realm 'realm': Change built-in flag is not possible");
        assertThatThrownBy(() -> builtIn.mergedWith(custom, "realm"))
                .isInstanceOf(InvalidAuthenticationFlowException.class)
                .hasMessageContaining("Deletion or creation of built-in flows is not possible");
    }
}
