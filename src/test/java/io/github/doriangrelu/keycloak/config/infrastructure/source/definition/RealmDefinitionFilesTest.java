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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RealmDefinitionFilesTest {

    private final RealmDefinitionFiles files =
            new RealmDefinitionFiles(Path.of("src/test/resources/definition/realms"));

    private RealmDefinition load(final String folder) {
        return this.files.load(folder).orElseThrow();
    }

    @Test
    void theFoldersOfTheRootAreTheRealms() {
        assertThat(this.files.folders()).containsExactly("broken", "split");
        assertThat(this.files.load("nope")).isEmpty();
    }

    @Test
    void aRealmSplitByTypeIsReadAsOneRealm() {
        RealmDefinition realm = load("split");

        assertThat(realm.realm()).map(TemplatedValue::raw).contains("{{env}}-mon-realm");
        assertThat(realm.isReadOnly()).isFalse();
        assertThat(realm.declaredTypes())
                .containsExactlyInAnyOrder(ElementType.CLIENT, ElementType.ROLE, ElementType.GROUP);
        assertThat(realm.clients())
                .extracting(
                        client -> client.definition().clientId().raw(),
                        client -> client.definition().applicationType())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("frontend", ApplicationType.SPA),
                        org.assertj.core.groups.Tuple.tuple("batch", ApplicationType.SERVICE),
                        org.assertj.core.groups.Tuple.tuple("legacy", ApplicationType.CUSTOM));
        Defined<ClientDefinition> frontend = realm.client("frontend").orElseThrow();
        assertThat(frontend.origin().file()).isEqualTo("20-clients.yaml");
        assertThat(frontend.origin().line()).isEqualTo(3);
        assertThat(frontend.definition().redirectUris()).containsExactly(TemplatedValue.of("{{appUrl}}/*"));
    }

    @Test
    void realmAndClientRolesAreRead() {
        assertThat(load("split").roles())
                .extracting(Defined::definition)
                .containsExactly(
                        new RoleDefinition(
                                TemplatedValue.of("reader"),
                                Optional.empty(),
                                Optional.of(TemplatedValue.of("Reads everything"))),
                        new RoleDefinition(
                                TemplatedValue.of("viewer"),
                                Optional.of(TemplatedValue.of("frontend")),
                                Optional.empty()));
    }

    @Test
    void subGroupsAreReadAsPathsParentsFirst() {
        assertThat(load("split").groups())
                .extracting(Defined::definition)
                .containsExactly(
                        new GroupDefinition("/team", TemplatedValue.of("team"), Set.of("reader"), Map.of()),
                        new GroupDefinition(
                                "/team/devs",
                                TemplatedValue.of("devs"),
                                Set.of(),
                                Map.of("frontend", Set.of("viewer"))));
        assertThat(load("split").groups().get(1).origin().file()).isEqualTo("40-groups.json");
    }

    @Test
    void anUnreadableFileMakesTheRealmReadOnly() {
        RealmDefinition realm = load("broken");

        assertThat(realm.isReadOnly()).isTrue();
        assertThat(realm.unreadableFiles()).singleElement().satisfies(file -> {
            assertThat(file.file()).isEqualTo("00-realm.yaml");
            assertThat(file.line()).isEqualTo(2);
        });
        assertThat(realm.clients())
                .extracting(client -> client.definition().clientId().raw())
                .containsExactly("app");
    }
}
