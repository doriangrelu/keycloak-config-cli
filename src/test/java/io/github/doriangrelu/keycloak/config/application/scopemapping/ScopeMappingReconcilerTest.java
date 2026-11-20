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

package io.github.doriangrelu.keycloak.config.application.scopemapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.scopemapping.port.ScopeMappingGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScopeMappingReconcilerTest {

    static final class InMemoryScopeMappings implements ScopeMappingGateway {
        final Map<ScopeMapping.Key, Set<String>> roles = new LinkedHashMap<>();

        @Override
        public List<ScopeMapping> findAll(String realm, boolean clientRoles) {
            return this.roles.entrySet().stream()
                    .filter(entry ->
                            !entry.getValue().isEmpty() && entry.getKey().isRealmRoles() != clientRoles)
                    .map(entry -> new ScopeMapping(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public void addRoles(String realm, ScopeMapping.Key key, Set<String> added) {
            this.roles.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(added);
        }

        @Override
        public void removeRoles(String realm, ScopeMapping.Key key, Set<String> removed) {
            this.roles.getOrDefault(key, new LinkedHashSet<>()).removeAll(removed);
        }
    }

    private static final ScopeMapping.Key APP = ScopeMapping.Key.ofClient("app", null);
    private static final ScopeMapping.Key PROFILE = ScopeMapping.Key.ofClientScope("profile", null);

    private InMemoryScopeMappings keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryScopeMappings();
        this.keycloak.addRoles("test", APP, Set.of("reader", "writer"));
        this.keycloak.addRoles("test", PROFILE, Set.of("reader"));
        this.engine = new ReconciliationEngine(List.of(ScopeMappingReconciler.ofRealmRoles(this.keycloak)));
    }

    private void run(ScopeMapping... declared) {
        RealmContext context = new RealmContext("test", true);
        this.engine.apply(context, new DesiredState().declare(ResourceType.SCOPE_MAPPING, List.of(declared)));
        this.engine.prune(context);
    }

    @Test
    void makesTheDeclaredRolesExactAndEmptiesUndeclaredMappings() {
        run(new ScopeMapping(APP, Set.of("reader", "admin")));

        assertThat(this.keycloak.roles.get(APP)).containsExactlyInAnyOrder("reader", "admin");
        assertThat(this.keycloak.roles.get(PROFILE)).isEmpty();
    }

    @Test
    void neverEmptiesTheMappingsKeycloakCreates() {
        ScopeMapping.Key offlineAccess = ScopeMapping.Key.ofClientScope("offline_access", null);
        this.keycloak.addRoles("test", offlineAccess, Set.of("offline_access"));

        run(new ScopeMapping(APP, Set.of("reader")));

        assertThat(this.keycloak.roles.get(offlineAccess)).containsExactly("offline_access");
    }
}
