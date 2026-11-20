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

package io.github.doriangrelu.keycloak.config.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComponentReconcilerTest {

    static final class InMemoryComponents implements ComponentGateway {
        final Map<Component.Key, Component> components = new LinkedHashMap<>();

        @Override
        public List<Component> findAll(String realm) {
            return new ArrayList<>(this.components.values());
        }

        @Override
        public void create(String realm, Component component) {
            if (!component.key().isTopLevel()) {
                assertThat(this.components).containsKey(component.key().parent());
            }
            this.components.put(component.key(), component);
        }

        @Override
        public void update(String realm, Component component) {
            this.components.put(component.key(), component);
        }

        @Override
        public void delete(String realm, Component.Key key) {
            this.components.keySet().removeIf(candidate -> isWithin(candidate, key));
        }

        private static boolean isWithin(Component.Key candidate, Component.Key key) {
            return candidate != null && (candidate.equals(key) || isWithin(candidate.parent(), key));
        }

        @Override
        public void syncImportingUserStorages(String realm) {}
    }

    private static final Component.Key LDAP = new Component.Key(null, "storage", null, "ldap");
    private static final Component.Key KEYS = new Component.Key(null, "custom-keys", null, "rsa");
    private static final Component.Key REALM_KEYS =
            new Component.Key(null, "org.keycloak.keys.KeyProvider", null, "rsa-generated");
    private static final Component.Key DEFAULT_MAPPER = new Component.Key(LDAP, "mapper", null, "username");
    private static final Component.Key KEY_CHILD = new Component.Key(KEYS, "child", null, "kept");

    private InMemoryComponents keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryComponents();
        this.keycloak.create("test", component(LDAP, Map.of("enabled", List.of("true"), "vendor", List.of("ad"))));
        this.keycloak.create("test", component(KEYS, Map.of()));
        this.keycloak.create("test", component(DEFAULT_MAPPER, Map.of()));
        this.keycloak.create("test", component(KEY_CHILD, Map.of()));
        this.engine = new ReconciliationEngine(List.of(
                ComponentReconciler.ofComponents(this.keycloak), ComponentReconciler.ofSubComponents(this.keycloak)));
    }

    private static Component component(Component.Key key, Map<String, List<String>> config) {
        return new Component(key, null, "provider", config);
    }

    private void run(Component... declared) {
        RealmContext context = new RealmContext("test", false);
        List<Component> topLevel = java.util.Arrays.stream(declared)
                .filter(component -> component.key().isTopLevel())
                .toList();
        List<Component> children = java.util.Arrays.stream(declared)
                .filter(component -> !component.key().isTopLevel())
                .toList();
        Set<String> parents =
                children.stream().map(c -> c.key().parent().path()).collect(Collectors.toSet());
        DesiredState desired = new DesiredState()
                .declare(ResourceType.COMPONENT, topLevel)
                .declare(ResourceType.SUB_COMPONENT, children, parents);
        this.engine.apply(context, desired);
        this.engine.prune(context);
    }

    @Test
    void declaredConfigEntriesReplaceTheCurrentOnesAndOthersAreKept() {
        Component current = component(LDAP, Map.of("enabled", List.of("true"), "vendor", List.of("ad")));

        Component merged =
                current.mergedWith(new Component(LDAP, "declared-id", null, Map.of("enabled", List.of("false"))));

        assertThat(merged.config()).containsEntry("enabled", List.of("false")).containsEntry("vendor", List.of("ad"));
        assertThat(merged.providerId()).isEqualTo("provider");
        assertThat(merged.id()).isNull();
    }

    @Test
    void deletesTheUndeclaredComponentsAndTheUndeclaredSubComponentsOfDeclaredComponents() {
        Component.Key declaredMapper = new Component.Key(LDAP, "mapper", null, "email");

        run(component(LDAP, Map.of()), component(declaredMapper, Map.of()));

        assertThat(this.keycloak.components).containsOnlyKeys(LDAP, declaredMapper);
    }

    @Test
    void aComponentDeclaredWithoutSubComponentsKeepsItsOwn() {
        run(component(LDAP, Map.of()));

        assertThat(this.keycloak.components).containsKeys(LDAP, DEFAULT_MAPPER);
    }

    @Test
    void neverDeletesTheKeysOfTheRealm() {
        this.keycloak.create("test", component(REALM_KEYS, Map.of()));

        run(component(LDAP, Map.of()));

        assertThat(this.keycloak.components).containsKey(REALM_KEYS);
    }
}
