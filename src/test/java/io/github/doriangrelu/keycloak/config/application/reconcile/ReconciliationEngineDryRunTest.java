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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReconciliationEngineDryRunTest {

    record Item(String name, String value) implements Resource<String> {
        @Override
        public String key() {
            return this.name;
        }

        @Override
        public String protectedName() {
            return this.name;
        }
    }

    /** Items in memory; counts the reads and the writes. */
    static final class Items implements Reconciler<String, Item> {
        final Map<String, Item> stored = new LinkedHashMap<>();
        final List<String> writes = new ArrayList<>();
        int reads;

        @Override
        public ResourceType type() {
            return ResourceType.MESSAGE_BUNDLE;
        }

        @Override
        public Set<ResourceType> dependsOn() {
            return Set.of();
        }

        @Override
        public List<Item> read(RealmContext context, Collection<Item> desired) {
            this.reads++;
            return new ArrayList<>(this.stored.values());
        }

        @Override
        public Item merge(RealmContext context, Item current, Item desired) {
            return desired;
        }

        @Override
        public void create(RealmContext context, Item resource) {
            this.writes.add("create " + resource.name());
        }

        @Override
        public void update(RealmContext context, Item current, Item target) {
            this.writes.add("update " + target.name());
        }

        @Override
        public void delete(RealmContext context, Item resource) {
            this.writes.add("delete " + resource.name());
        }

        @Override
        public boolean isOwnedByKeycloak(RealmContext context, Item resource) {
            return false;
        }
    }

    private static RealmContext dryRun() {
        return new RealmContext("realm", false, true);
    }

    @Test
    void plansCreationsUpdatesAndDeletionsWithoutWritingAnything() {
        Items items = new Items();
        items.stored.put("kept", new Item("kept", "old"));
        items.stored.put("gone", new Item("gone", "x"));
        ReconciliationEngine engine = new ReconciliationEngine(List.of(items));
        RealmContext context = dryRun();

        engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.MESSAGE_BUNDLE, List.of(new Item("kept", "new"), new Item("added", "y"))));
        engine.prune(context);

        assertThat(items.writes).isEmpty();
        assertThat(context.plan())
                .extracting(PlannedChange::key, PlannedChange::kind)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("kept", PlannedChange.Kind.UPDATE),
                        org.assertj.core.groups.Tuple.tuple("added", PlannedChange.Kind.CREATE),
                        org.assertj.core.groups.Tuple.tuple("gone", PlannedChange.Kind.DELETE));
        PlannedChange update = context.plan().stream()
                .filter(change -> change.kind() == PlannedChange.Kind.UPDATE)
                .findFirst()
                .orElseThrow();
        assertThat(update.before()).isEqualTo(new Item("kept", "old"));
        assertThat(update.after()).isEqualTo(new Item("kept", "new"));
    }

    @Test
    void aMissingRealmIsNeverReadAndEverythingIsCreated() {
        Items items = new Items();
        ReconciliationEngine engine = new ReconciliationEngine(List.of(items));
        RealmContext context = dryRun();
        context.realmIsMissing();

        engine.apply(context, new DesiredState().declare(ResourceType.MESSAGE_BUNDLE, List.of(new Item("a", "1"))));
        engine.prune(context);

        assertThat(items.reads).isZero();
        assertThat(items.writes).isEmpty();
        assertThat(context.plan()).extracting(PlannedChange::kind).containsExactly(PlannedChange.Kind.CREATE);
    }
}
