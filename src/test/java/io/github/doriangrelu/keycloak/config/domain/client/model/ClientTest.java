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

package io.github.doriangrelu.keycloak.config.domain.client.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClientTest {

    private static Client client(String clientId, String name) {
        return new Client(clientId, name, null, null, null, null, null, null, null, null, null);
    }

    private static Client authorizationClient(Boolean bearerOnly, Boolean publicClient, Boolean serviceAccounts) {
        return new Client("app", "App", null, null, null, null, null, bearerOnly, publicClient, serviceAccounts, null);
    }

    @Test
    void aClientNeedsAnIdOrAName() {
        assertThatThrownBy(() -> client(null, null)).hasMessage("clients require client id or name.");
        assertThat(client(null, "my client").key()).isEqualTo("name:my client");
        assertThat(client("app", "my client").key()).isEqualTo("app");
    }

    @Test
    void unspecifiedFieldsKeepTheirValue() {
        Client current = new Client(
                "app",
                "App",
                Map.of("a", "1"),
                Set.of("profile"),
                Set.of("email"),
                null,
                "secret",
                false,
                false,
                true,
                new UnmodeledAttributes(Map.of("enabled", true)));

        assertThat(current.mergedWith(client("app", null))).isEqualTo(current);
    }

    @Test
    void attributesAreMergedAndScopesReplaced() {
        Client current = new Client(
                "app",
                null,
                Map.of("a", "1", "b", "2"),
                Set.of("profile", "email"),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        Client desired =
                new Client("app", null, Map.of("b", "3"), Set.of("roles"), null, null, null, null, null, null, null);

        Client merged = current.mergedWith(desired);

        assertThat(merged.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of("a", "1", "b", "3"));
        assertThat(merged.defaultClientScopes()).containsExactly("roles");
    }

    @Test
    void messagesUseTheNameUnlessItIsGeneratedByKeycloak() {
        assertThat(client("app", "My app").identifier()).isEqualTo("My app");
        assertThat(client("account", "${client_account}").identifier()).isEqualTo("account");
        assertThat(client(null, "${client_realm-management}").identifier()).isEqualTo("${client_realm-management}");
    }

    @Test
    void keycloakClientsAreNeverDeleted() {
        assertThat(client("account", "${client_account}").isOwnedByKeycloak("test"))
                .isTrue();
        assertThat(client("admin-permissions", null).isOwnedByKeycloak("test")).isTrue();
        assertThat(client("test-realm", null).isOwnedByKeycloak("master")).isTrue();
        assertThat(client("test-realm", null).isOwnedByKeycloak("test")).isFalse();
        assertThat(client("app", "App").isOwnedByKeycloak("test")).isFalse();
    }

    @Test
    void authorizationSettingsNeedAConfidentialClientWithAServiceAccount() {
        assertThatThrownBy(() -> authorizationClient(true, null, true).requireSupportForAuthorizationSettings("r"))
                .hasMessage("Unsupported authorization settings for client 'App' in realm 'r': client must be"
                        + " confidential.");
        assertThatThrownBy(() -> authorizationClient(null, null, false).requireSupportForAuthorizationSettings("r"))
                .hasMessage("Unsupported authorization settings for client 'App' in realm 'r': serviceAccountsEnabled"
                        + " must be 'true'.");
        authorizationClient(false, false, true).requireSupportForAuthorizationSettings("r");
        client("realm-management", null).requireSupportForAuthorizationSettings("r");
    }
}
