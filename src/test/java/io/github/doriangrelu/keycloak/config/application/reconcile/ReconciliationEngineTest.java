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

package io.github.doriangrelu.keycloak.config.application.reconcile;

import static io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType.CLIENT_ROLE;
import static io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType.REALM_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.common.ConflictingDefinitionException;
import io.github.doriangrelu.keycloak.config.domain.common.ManagedMode;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule.Matching;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {

    record Item(String key, String value) implements Resource<String> {
        @Override
        public String name() {
            return this.key;
        }
    }

    /** Keeps resources in memory and logs every write as "TYPE action key". */
    static final class FakeReconciler implements Reconciler<String, Item> {
        private final ResourceType type;
        private final Set<ResourceType> dependsOn;
        private final Map<String, Item> store = new LinkedHashMap<>();
        private final List<String> log;

        FakeReconciler(ResourceType type, Set<ResourceType> dependsOn, List<String> log, Item... existing) {
            this.type = type;
            this.dependsOn = dependsOn;
            this.log = log;
            for (Item item : existing) {
                this.store.put(item.key(), item);
            }
        }

        @Override
        public ResourceType type() {
            return this.type;
        }

        @Override
        public Set<ResourceType> dependsOn() {
            return this.dependsOn;
        }

        @Override
        public List<Item> read(RealmContext context, Collection<Item> desired) {
            return List.copyOf(this.store.values());
        }

        @Override
        public Item merge(Item current, Item desired) {
            return new Item(current.key(), desired.value() == null ? current.value() : desired.value());
        }

        @Override
        public void create(RealmContext context, Item resource) {
            this.log.add(this.type + " create " + resource.key());
            this.store.put(resource.key(), resource);
        }

        @Override
        public void update(RealmContext context, Item current, Item target) {
            this.log.add(this.type + " update " + target.key());
            this.store.put(target.key(), target);
        }

        @Override
        public void delete(RealmContext context, Item resource) {
            this.log.add(this.type + " delete " + resource.key());
            this.store.remove(resource.key());
        }

        @Override
        public String stateScope(Item resource) {
            return this.type.name();
        }

        @Override
        public boolean isOwnedByKeycloak(Item resource) {
            return resource.key().startsWith("default-");
        }
    }

    private final List<String> log = new ArrayList<>();

    private static RealmContext context(boolean remoteState) {
        return new RealmContext("test", false, remoteState, Map.of());
    }

    @Test
    void appliesTypesInDependencyOrder() {
        FakeReconciler clientRoles = new FakeReconciler(CLIENT_ROLE, Set.of(REALM_ROLE), this.log);
        FakeReconciler realmRoles = new FakeReconciler(REALM_ROLE, Set.of(), this.log);
        ReconciliationEngine engine = new ReconciliationEngine(List.of(clientRoles, realmRoles));

        engine.apply(
                context(false),
                new DesiredState()
                        .declare(CLIENT_ROLE, List.of(new Item("c", "1")))
                        .declare(REALM_ROLE, List.of(new Item("r", "1"))));

        assertThat(engine.order()).containsExactly(REALM_ROLE, CLIENT_ROLE);
        assertThat(this.log).containsExactly("REALM_ROLE create r", "CLIENT_ROLE create c");
    }

    @Test
    void appliesOnlyWhatDiffersAndNeverDeletesWhileApplying() {
        FakeReconciler realmRoles = new FakeReconciler(
                REALM_ROLE,
                Set.of(),
                this.log,
                new Item("same", "1"),
                new Item("changed", "1"),
                new Item("orphan", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles));
        RealmContext context = context(false);

        engine.apply(
                context,
                new DesiredState().declare(REALM_ROLE, List.of(new Item("same", "1"), new Item("changed", "2"))));

        assertThat(this.log).containsExactly("REALM_ROLE update changed");
        assertThat(context.plan()).extracting(PlannedChange::kind).containsExactly(PlannedChange.Kind.UPDATE);
    }

    @Test
    void prunesOnceWhatIsMissingFromEveryFileOfTheRealm() {
        FakeReconciler realmRoles = new FakeReconciler(
                REALM_ROLE,
                Set.of(),
                this.log,
                new Item("from-a", "1"),
                new Item("from-b", "1"),
                new Item("orphan", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles));
        RealmContext context = context(false);

        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("from-a", null))));
        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("from-b", null))));
        engine.prune(context);
        engine.prune(context);

        assertThat(this.log).containsExactly("REALM_ROLE delete orphan");
    }

    @Test
    void registeredFilesAreKeptWithoutBeingApplied() {
        FakeReconciler realmRoles = new FakeReconciler(
                REALM_ROLE, Set.of(), this.log, new Item("unchanged-file", "1"), new Item("orphan", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles));
        RealmContext context = context(false);

        engine.register(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("unchanged-file", "2"))));
        engine.prune(context);

        assertThat(this.log).containsExactly("REALM_ROLE delete orphan");
    }

    @Test
    void prunesInReverseDependencyOrder() {
        FakeReconciler realmRoles = new FakeReconciler(REALM_ROLE, Set.of(), this.log, new Item("r", "1"));
        FakeReconciler clientRoles = new FakeReconciler(CLIENT_ROLE, Set.of(REALM_ROLE), this.log, new Item("c", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles, clientRoles));
        RealmContext context = context(false);

        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of()).declare(CLIENT_ROLE, List.of()));
        engine.prune(context);

        assertThat(this.log).containsExactly("CLIENT_ROLE delete c", "REALM_ROLE delete r");
    }

    @Test
    void neverPrunesUndeclaredTypes() {
        FakeReconciler realmRoles = new FakeReconciler(REALM_ROLE, Set.of(), this.log, new Item("r", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles));
        RealmContext context = context(false);

        engine.apply(context, new DesiredState());
        engine.prune(context);

        assertThat(this.log).isEmpty();
    }

    @Test
    void prunesOnlyWhatThePolicyAllows() {
        FakeReconciler realmRoles = new FakeReconciler(
                REALM_ROLE,
                Set.of(),
                this.log,
                new Item("created-by-tool", "1"),
                new Item("created-by-hand", "1"),
                new Item("protected", "1"),
                new Item("default-roles", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles));
        RealmContext context = context(true);
        context.captureState("REALM_ROLE", List.of("created-by-tool", "protected", "default-roles"));
        context.addProtections(List.of(new ProtectionRule(Matching.EQUALS, "protected")));

        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of()));
        engine.prune(context);

        assertThat(this.log).containsExactly("REALM_ROLE delete created-by-tool");
    }

    @Test
    void noDeleteModeKeepsEverything() {
        FakeReconciler realmRoles = new FakeReconciler(REALM_ROLE, Set.of(), this.log, new Item("r", "1"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(realmRoles));
        RealmContext context = new RealmContext("test", false, false, Map.of(REALM_ROLE, ManagedMode.NO_DELETE));

        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of()));
        engine.prune(context);

        assertThat(this.log).isEmpty();
    }

    @Test
    void conflictingDefinitionsInTwoFilesAreRejected() {
        ReconciliationEngine engine =
                new ReconciliationEngine(List.of(new FakeReconciler(REALM_ROLE, Set.of(), this.log)));
        RealmContext context = context(false);
        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("admin", "1"))));

        assertThatThrownBy(() ->
                        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("admin", "2")))))
                .isInstanceOf(ConflictingDefinitionException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void identicalDefinitionsInTwoFilesAreAccepted() {
        ReconciliationEngine engine =
                new ReconciliationEngine(List.of(new FakeReconciler(REALM_ROLE, Set.of(), this.log)));
        RealmContext context = context(false);

        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("admin", "1"))));
        engine.apply(context, new DesiredState().declare(REALM_ROLE, List.of(new Item("admin", "1"))));

        assertThat(this.log).containsExactly("REALM_ROLE create admin");
    }

    @Test
    void cyclicDependenciesAreRejected() {
        List<Reconciler<?, ?>> cyclic = List.of(
                new FakeReconciler(REALM_ROLE, Set.of(CLIENT_ROLE), this.log),
                new FakeReconciler(CLIENT_ROLE, Set.of(REALM_ROLE), this.log));

        assertThatThrownBy(() -> new ReconciliationEngine(cyclic)).isInstanceOf(DependencyCycleException.class);
    }
}
