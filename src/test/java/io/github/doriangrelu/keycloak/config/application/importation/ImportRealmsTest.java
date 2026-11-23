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

package io.github.doriangrelu.keycloak.config.application.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.application.importation.port.RealmDeclarationParser;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportRealmsTest {

    record Item(String key) implements Resource<String> {
        @Override
        public String protectedName() {
            return this.key;
        }
    }

    /** Keeps resources in memory and logs every write as "TYPE action key". */
    static final class FakeReconciler implements Reconciler<String, Item> {
        private final ResourceType type;
        private final Map<String, Item> store = new LinkedHashMap<>();
        private final List<String> log;

        FakeReconciler(final ResourceType type, final List<String> log, final String... existing) {
            this.type = type;
            this.log = log;
            for (String key : existing) {
                this.store.put(key, new Item(key));
            }
        }

        @Override
        public ResourceType type() {
            return this.type;
        }

        @Override
        public Set<ResourceType> dependsOn() {
            return this.type == ResourceType.REALM_SETTINGS ? Set.of() : Set.of(ResourceType.REALM_SETTINGS);
        }

        @Override
        public List<Item> read(final RealmContext context, final Collection<Item> desired) {
            this.log.add(this.type + " read");
            return List.copyOf(this.store.values());
        }

        @Override
        public Item merge(final RealmContext context, final Item current, final Item desired) {
            return desired;
        }

        @Override
        public void create(final RealmContext context, final Item resource) {
            this.log.add(this.type + " create " + resource.key());
            this.store.put(resource.key(), resource);
        }

        @Override
        public void update(final RealmContext context, final Item current, final Item target) {
            this.log.add(this.type + " update " + target.key());
        }

        @Override
        public void delete(final RealmContext context, final Item resource) {
            this.log.add(this.type + " delete " + resource.key());
            this.store.remove(resource.key());
        }

        @Override
        public boolean isOwnedByKeycloak(final RealmContext context, final Item resource) {
            return false;
        }
    }

    /** A realm declaring its settings and some clients. */
    record Declaration(
            String realm, List<String> clients, boolean declaresUserStorage, List<ProtectionRule> protections)
            implements RealmDeclaration {

        Declaration(final String realm, final String... clients) {
            this(realm, List.of(clients), false, List.of());
        }

        @Override
        public String source() {
            return "file:/" + this.realm + ".json";
        }

        @Override
        public DesiredState settings() {
            return new DesiredState().declare(ResourceType.REALM_SETTINGS, List.of(new Item(this.realm)));
        }

        @Override
        public DesiredState desiredState(final boolean realmExists) {
            return settings()
                    .declare(
                            ResourceType.CLIENT,
                            this.clients.stream().map(Item::new).toList());
        }
    }

    private final List<String> log = new ArrayList<>();
    private final RealmGateway realms = mock(RealmGateway.class);
    private final ComponentGateway components = mock(ComponentGateway.class);
    private final ImportFileSource files = mock(ImportFileSource.class);
    private final RealmDeclarationParser parser = mock(RealmDeclarationParser.class);

    private ImportRealms importRealms(final ImportOptions options, final String... existingClients) {
        ReconciliationEngine engine = new ReconciliationEngine(List.of(
                new FakeReconciler(ResourceType.REALM_SETTINGS, this.log, "demo"),
                new FakeReconciler(ResourceType.CLIENT, this.log, existingClients)));
        return new ImportRealms(this.files, this.parser, this.realms, this.components, engine, options);
    }

    private void realmExists(final boolean exists) {
        when(this.realms.findSettings(anyString()))
                .thenReturn(exists ? Optional.of(mock(RealmSettings.class)) : Optional.empty());
    }

    @Test
    void theFilesOfARealmAreAppliedOneByOneThenTheRealmIsCleanedOnce() {
        realmExists(true);
        ImportRealms importRealms = importRealms(new ImportOptions(false, false), "a", "orphan");

        ImportReport report =
                importRealms.importRealms(List.of(new Declaration("demo", "a"), new Declaration("demo", "b")));

        // The client of the second file is not deleted by the first one; what no file declares is deleted at the end
        assertThat(this.log)
                .filteredOn(line -> !line.endsWith(" read"))
                .containsExactly("CLIENT create b", "CLIENT delete orphan");
        assertThat(report.plan("demo"))
                .extracting(PlannedChange::kind)
                .containsExactly(PlannedChange.Kind.CREATE, PlannedChange.Kind.DELETE);
    }

    @Test
    void nothingIsDeletedWithoutTheCleanPhase() {
        realmExists(true);
        ImportRealms importRealms = importRealms(new ImportOptions(false, false), "orphan");

        importRealms.importRealmsWithoutCleanup(List.of(new Declaration("demo", "a")));

        assertThat(this.log).filteredOn(line -> !line.endsWith(" read")).containsExactly("CLIENT create a");
    }

    @Test
    void aDryRunOnAMissingRealmPlansEverythingAsCreationsAndReadsNothing() {
        realmExists(false);
        ImportRealms importRealms = importRealms(new ImportOptions(true, true), "orphan");

        ImportReport report =
                importRealms.importRealms(List.of(new Declaration("new-realm", List.of("a"), true, List.of())));

        assertThat(this.log).isEmpty();
        assertThat(report.plan("new-realm"))
                .extracting(PlannedChange::type, PlannedChange::kind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ResourceType.REALM_SETTINGS, PlannedChange.Kind.CREATE),
                        org.assertj.core.groups.Tuple.tuple(ResourceType.CLIENT, PlannedChange.Kind.CREATE));
        verify(this.components, never()).syncImportingUserStorages(anyString());
    }

    @Test
    void theUserStoragesAreSynchronizedWhenAskedAndDeclared() {
        realmExists(true);

        importRealms(new ImportOptions(false, true))
                .importRealms(List.of(new Declaration("demo", List.of(), true, List.of())));
        importRealms(new ImportOptions(false, true)).importRealms(List.of(new Declaration("other")));
        importRealms(new ImportOptions(false, false))
                .importRealms(List.of(new Declaration("third", List.of(), true, List.of())));

        verify(this.components).syncImportingUserStorages("demo");
        verify(this.components, never()).syncImportingUserStorages("other");
        verify(this.components, never()).syncImportingUserStorages("third");
    }

    @Test
    void protectedResourcesAreKeptByTheCleanPhase() {
        realmExists(true);
        ImportRealms importRealms = importRealms(new ImportOptions(false, false), "legacy", "orphan");

        importRealms.importRealms(List.of(new Declaration(
                "demo", List.of(), false, List.of(new ProtectionRule(ProtectionRule.Matching.EQUALS, "legacy")))));

        assertThat(this.log).filteredOn(line -> !line.endsWith(" read")).containsExactly("CLIENT delete orphan");
    }

    @Test
    void everyFileIsParsedBeforeAnythingIsChanged() {
        realmExists(true);
        ImportFile valid = new ImportFile("location", "file:/1.json", "{}");
        ImportFile invalid = new ImportFile("location", "file:/2.json", "{");
        when(this.files.read(List.of("location"))).thenReturn(List.of(valid, invalid));
        when(this.parser.parse(valid)).thenReturn(List.of(new Declaration("demo", "a")));
        when(this.parser.parse(invalid)).thenThrow(new InvalidImportException("Unable to parse file 'file:/2.json'"));

        assertThatThrownBy(() -> importRealms(new ImportOptions(false, false)).importFiles(List.of("location")))
                .isInstanceOf(InvalidImportException.class);
        assertThat(this.log).isEmpty();
    }
}
