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

import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyViewsTest {

    private static final String REALM = "realm: \"{{env}}-mon-realm\"\n";

    @TempDir
    Path root;

    private Path folder;
    private RealmDefinitionFiles files;

    @BeforeEach
    void setUp() throws IOException {
        this.folder = Files.createDirectories(this.root.resolve("demo"));
        this.files = new RealmDefinitionFiles(this.root);
    }

    private void write(final String name, final String content) throws IOException {
        Files.writeString(this.folder.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static KeyValue scalar(final String text) {
        return new KeyValue.Scalar(TemplatedValue.of(text));
    }

    private static KeyPath key(final String path) {
        return KeyPath.parse(path);
    }

    private ElementKeys onlyElement() {
        return this.files.elementKeys("demo").values().iterator().next();
    }

    private ElementKeys elementWith(final String key, final String text) {
        return this.files.elementKeys("demo").values().stream()
                .filter(keys -> keys.get(key(key)).equals(Optional.of(scalar(text))))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void anAttributeWithDotsIsOneSegment() throws IOException {
        write(
                "20-clients.yaml",
                REALM + "clients:\n  - clientId: app\n    attributes:\n      pkce.code.challenge.method: S256\n");

        assertThat(onlyElement().get(key("attributes.\"pkce.code.challenge.method\"")))
                .contains(scalar("S256"));
    }

    @Test
    void aClientWithoutFlowsShowsTheDefaultsOfKeycloak() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n");

        ElementKeys keys = onlyElement();

        assertThat(keys.get(key("publicClient"))).contains(scalar("false"));
        assertThat(keys.get(key("standardFlowEnabled"))).contains(scalar("true"));
        assertThat(keys.get(key("implicitFlowEnabled"))).contains(scalar("false"));
        assertThat(keys.get(key("directAccessGrantsEnabled"))).contains(scalar("true"));
        assertThat(keys.get(key("serviceAccountsEnabled"))).contains(scalar("false"));
    }

    @Test
    void aWrittenFlowKeepsItsValue() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n    publicClient: true\n");

        assertThat(onlyElement().get(key("publicClient"))).contains(scalar("true"));
    }

    @Test
    void listsOfValuesAreListedAndListsOfObjectsHaveNoShape() throws IOException {
        write(
                "20-clients.yaml",
                REALM + "clients:\n  - clientId: app\n    redirectUris:\n      - \"{{appUrl}}/*\"\n"
                        + "    protocolMappers:\n      - name: audience\n");

        ElementKeys keys = onlyElement();

        assertThat(keys.get(key("redirectUris")))
                .contains(new KeyValue.Listed(List.of(TemplatedValue.of("{{appUrl}}/*"))));
        assertThat(keys.get(key("protocolMappers"))).contains(new KeyValue.Shapeless());
    }

    @Test
    void aNullKeyIsAbsent() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n    description:\n");

        assertThat(onlyElement().get(key("description"))).isEmpty();
    }

    @Test
    void aClientRoleKnowsItsClientAndARealmRoleDoesNot() throws IOException {
        write(
                "30-roles.yaml",
                REALM + "roles:\n  realm:\n    - name: reader\n  client:\n    frontend:\n      - name: viewer\n");

        assertThat(this.files.elementKeys("demo")).hasSize(2);
        assertThat(elementWith("name", "reader").get(key("client"))).isEmpty();
        assertThat(elementWith("name", "viewer").get(key("client"))).contains(scalar("frontend"));
    }

    @Test
    void aNestedGroupHasItsPathAndItsClientRoles() throws IOException {
        write(
                "40-groups.yaml",
                REALM + "groups:\n  - name: team\n    subGroups:\n      - name: devs\n"
                        + "        clientRoles:\n          frontend:\n            - viewer\n");

        assertThat(this.files.elementKeys("demo")).hasSize(2);
        ElementKeys devs = elementWith("path", "/team/devs");
        assertThat(devs.get(key("name"))).contains(scalar("devs"));
        assertThat(devs.get(key("clientRoles.frontend")))
                .contains(new KeyValue.Listed(List.of(TemplatedValue.of("viewer"))));
        assertThat(elementWith("path", "/team").get(key("subGroups"))).isEmpty();
    }

    @Test
    void theElementsAreKeyedByTheOriginOfLoad() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n");
        Origin loaded =
                this.files.load("demo").orElseThrow().clients().getFirst().origin();

        assertThat(this.files.elementKeys("demo")).containsOnlyKeys(loaded);
    }

    @Test
    void anUnreadableFileHasNoElement() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n");
        write("30-roles.yaml", "roles: [unclosed\n");

        Map<Origin, ElementKeys> views = this.files.elementKeys("demo");

        assertThat(views).hasSize(1);
    }

    @Test
    void theRealmKeysLeaveTheElementsOut() {
        RealmDefinitionFiles split = new RealmDefinitionFiles(Path.of("src/test/resources/definition/realms"));

        ElementKeys keys = split.realmKeys("split");

        assertThat(keys.get(key("realm"))).isPresent();
        assertThat(keys.get(key("enabled"))).contains(scalar("true"));
        assertThat(keys.values().keySet().stream().map(KeyPath::toString))
                .noneMatch(path -> path.startsWith("clients") || path.startsWith("roles") || path.startsWith("groups"));
    }

    @Test
    void theRolesAndGroupsOfASplitRealmAreKeyedByTheOriginsOfLoad() {
        RealmDefinitionFiles split = new RealmDefinitionFiles(Path.of("src/test/resources/definition/realms"));
        var realm = split.load("split").orElseThrow();
        var views = split.elementKeys("split");

        assertThat(realm.roles()).isNotEmpty();
        assertThat(realm.groups()).isNotEmpty();
        assertThat(realm.roles()).allSatisfy(defined -> assertThat(views).containsKey(defined.origin()));
        assertThat(realm.groups()).allSatisfy(defined -> assertThat(views).containsKey(defined.origin()));
    }
}
