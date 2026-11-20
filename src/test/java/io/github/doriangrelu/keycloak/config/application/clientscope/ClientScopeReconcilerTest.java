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

package io.github.doriangrelu.keycloak.config.application.clientscope;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.clientscope.port.ClientScopeGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientScopeReconcilerTest {

    static final class InMemoryClientScopes implements ClientScopeGateway {
        final Map<String, ClientScope> scopes = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        @Override
        public List<ClientScope> findAll(String realm) {
            return List.copyOf(this.scopes.values());
        }

        @Override
        public void create(String realm, ClientScope clientScope) {
            this.log.add("create " + clientScope.name());
            this.scopes.put(clientScope.name(), clientScope);
        }

        @Override
        public void update(String realm, ClientScope current, ClientScope target) {
            this.log.add("update " + target.name());
            this.scopes.put(target.name(), target);
        }

        @Override
        public void delete(String realm, String name) {
            this.log.add("delete " + name);
            this.scopes.remove(name);
        }
    }

    private InMemoryClientScopes keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryClientScopes();
        this.keycloak.scopes.put("profile", new ClientScope("profile", null, null, null, List.of(), true, null));
        this.keycloak.scopes.put("orphan", new ClientScope("orphan", null, null, null, List.of(), false, null));
        this.engine = new ReconciliationEngine(List.of(new ClientScopeReconciler(this.keycloak)));
    }

    private static DesiredState declaring(ClientScope... scopes) {
        return new DesiredState().declare(ResourceType.CLIENT_SCOPE, List.of(scopes));
    }

    @Test
    void deletesMissingScopesButNotTheRealmDefaultOnesEvenWithRemoteState() {
        RealmContext context = new RealmContext("test", false, true);

        this.engine.apply(context, declaring(new ClientScope("added", "d", null, null, null, false, null)));
        this.engine.prune(context);

        assertThat(this.keycloak.log).containsExactly("create added", "delete orphan");
        assertThat(this.keycloak.scopes).containsOnlyKeys("profile", "added");
    }

    @Test
    void neverDeletesTheScopesKeycloakCreatesEvenWhenTheRealmNoLongerListsThem() {
        this.keycloak.scopes.put(
                "service_account", new ClientScope("service_account", null, null, null, null, false, null));
        RealmContext context = new RealmContext("test", false, false);

        this.engine.apply(context, declaring());
        this.engine.prune(context);

        assertThat(this.keycloak.scopes)
                .containsKeys("profile", "service_account")
                .doesNotContainKey("orphan");
    }
}
