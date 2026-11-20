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

package io.github.doriangrelu.keycloak.config.application.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.client.port.ClientGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientReconcilerTest {

    static final class InMemoryClients implements ClientGateway {
        final Map<String, Client> clients = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();
        Set<String> lastSecretRequest = Set.of();

        @Override
        public List<Client> findAll(String realm, Set<String> withSecretOf) {
            this.lastSecretRequest = withSecretOf;
            return List.copyOf(this.clients.values());
        }

        @Override
        public void create(String realm, Client client) {
            this.log.add("create " + client.key());
            this.clients.put(client.key(), client);
        }

        @Override
        public void update(String realm, Client current, Client target) {
            this.log.add("update " + target.key());
            this.clients.put(target.key(), target);
        }

        @Override
        public void delete(String realm, String clientId) {
            this.log.add("delete " + clientId);
            this.clients.remove(clientId);
        }
    }

    private InMemoryClients keycloak;
    private ReconciliationEngine engine;

    private static Client client(String clientId, String name, String secret) {
        return new Client(clientId, name, null, null, null, null, secret, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryClients();
        this.keycloak.clients.put("account", client("account", "${client_account}", null));
        this.keycloak.clients.put("old-app", client("old-app", "Old", null));
        this.keycloak.clients.put("app", client("app", "App", "s1"));
        this.engine = new ReconciliationEngine(List.of(new ClientReconciler(this.keycloak)));
    }

    @Test
    void createsUpdatesAndPrunesSparingKeycloakClients() {
        RealmContext context = new RealmContext("test", false, false);

        this.engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.CLIENT,
                                List.of(client("app", null, "s2"), client("new-app", null, null))));
        this.engine.prune(context);

        assertThat(this.keycloak.log).containsExactly("update app", "create new-app", "delete old-app");
        assertThat(this.keycloak.lastSecretRequest).containsExactly("app");
    }

    @Test
    void systemClientsAreNeverCreated() {
        DesiredState part =
                new DesiredState().declare(ResourceType.CLIENT, List.of(client("realm-management", null, null)));

        assertThatThrownBy(() -> this.engine.apply(new RealmContext("test", false, false), part))
                .isInstanceOf(ClientRejectedException.class)
                .hasMessage("Cannot create system client 'realm-management' in realm 'test': System clients should be"
                        + " auto-created by Keycloak");
    }
}
