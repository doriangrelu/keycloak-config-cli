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

package io.github.doriangrelu.keycloak.config.application.realm;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RealmReconcilersTest {

    private static final String OWN = "io.github.doriangrelu.keycloak.config";

    static final class InMemoryRealm implements RealmGateway {
        RealmSettings settings;
        final Map<DefaultClientScopes.Kind, Set<String>> scopes = new EnumMap<>(DefaultClientScopes.Kind.class);

        InMemoryRealm() {
            this.scopes.put(DefaultClientScopes.Kind.DEFAULT, new LinkedHashSet<>(Set.of("profile", "email")));
            this.scopes.put(DefaultClientScopes.Kind.OPTIONAL, new LinkedHashSet<>(Set.of("phone")));
        }

        @Override
        public Optional<RealmSettings> findSettings(String realm) {
            return Optional.ofNullable(this.settings);
        }

        @Override
        public void create(RealmSettings settings) {
            this.settings = settings;
        }

        @Override
        public void update(RealmSettings settings) {
            this.settings = settings;
        }

        @Override
        public DefaultClientScopes findDefaultClientScopes(String realm, DefaultClientScopes.Kind kind) {
            return new DefaultClientScopes(kind, this.scopes.get(kind));
        }

        @Override
        public void addDefaultClientScopes(String realm, DefaultClientScopes.Kind kind, Set<String> added) {
            this.scopes.values().forEach(existing -> assertThat(existing).doesNotContainAnyElementsOf(added));
            this.scopes.get(kind).addAll(added);
        }

        @Override
        public void removeDefaultClientScopes(String realm, DefaultClientScopes.Kind kind, Set<String> removed) {
            this.scopes.get(kind).removeAll(removed);
        }

        @Override
        public DefaultGroups findDefaultGroups(String realm) {
            return new DefaultGroups(Set.of());
        }

        @Override
        public void addDefaultGroups(String realm, Set<String> paths) {}

        @Override
        public void removeDefaultGroups(String realm, Set<String> paths) {}

        @Override
        public RealmFlowBindings findFlowBindings(String realm) {
            return new RealmFlowBindings(Map.of());
        }

        @Override
        public void bindFlows(String realm, RealmFlowBindings bindings) {}
    }

    @Test
    void declaredSettingsOverrideTheCurrentOnesAndTheStateAttributesAreKept() {
        RealmSettings current = new RealmSettings(
                "realm",
                new UnmodeledAttributes(Map.of(
                        "enabled", true,
                        "eventsEnabled", true,
                        "attributes", Map.of(OWN + ".state-key-roles", "[]", "custom", "old"))));
        RealmSettings desired = new RealmSettings(
                "realm", new UnmodeledAttributes(Map.of("enabled", false, "attributes", Map.of("custom", "new"))));

        RealmSettings merged = current.mergedWith(desired, OWN);

        assertThat(merged.settings().values())
                .containsEntry("enabled", false)
                .containsEntry("eventsEnabled", true)
                .containsEntry("attributes", Map.of("custom", "new", OWN + ".state-key-roles", "[]"));
    }

    @Test
    void aScopeChangingKindLeavesTheOtherKindFirst() {
        InMemoryRealm keycloak = new InMemoryRealm();
        ReconciliationEngine engine = new ReconciliationEngine(List.of(new DefaultClientScopesReconciler(keycloak)));
        RealmContext context = new RealmContext("realm", false, false);

        engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.DEFAULT_CLIENT_SCOPE,
                                List.of(
                                        new DefaultClientScopes(
                                                DefaultClientScopes.Kind.DEFAULT, Set.of("profile", "phone")),
                                        new DefaultClientScopes(DefaultClientScopes.Kind.OPTIONAL, Set.of("email")))));

        assertThat(keycloak.scopes.get(DefaultClientScopes.Kind.DEFAULT)).containsExactlyInAnyOrder("profile", "phone");
        assertThat(keycloak.scopes.get(DefaultClientScopes.Kind.OPTIONAL)).containsExactly("email");
    }

    @Test
    void eachFileAppliesItsOwnSettingsWithoutConflict() {
        InMemoryRealm keycloak = new InMemoryRealm();
        ReconciliationEngine engine = new ReconciliationEngine(List.of(new RealmSettingsReconciler(keycloak, OWN)));
        RealmContext context = new RealmContext("realm", false, false);

        engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.REALM_SETTINGS,
                                List.of(new RealmSettings("realm", new UnmodeledAttributes(Map.of("enabled", true))))));
        engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.REALM_SETTINGS,
                                List.of(new RealmSettings(
                                        "realm", new UnmodeledAttributes(Map.of("displayName", "Realm"))))));

        assertThat(keycloak.settings.settings().values())
                .containsEntry("enabled", true)
                .containsEntry("displayName", "Realm");
    }
}
