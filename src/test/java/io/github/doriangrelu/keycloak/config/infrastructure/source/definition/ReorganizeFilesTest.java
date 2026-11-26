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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.application.definition.ReorganizationInterruptedException;
import io.github.doriangrelu.keycloak.config.application.definition.ReorganizationRefusedException;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan.Move;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan.Rename;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class ReorganizeFilesTest {

    private static final Path RESOURCES = Path.of("src/test/resources/definition/reorganize");

    @TempDir
    Path root;

    private RealmDefinitionFiles files;

    private void copy(final String fixture) throws IOException {
        Path source = RESOURCES.resolve(fixture);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = this.root.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
        this.files = new RealmDefinitionFiles(this.root);
    }

    private Path folder() {
        return this.root.resolve("demo");
    }

    private String read(final String name) throws IOException {
        return Files.readString(folder().resolve(name), StandardCharsets.UTF_8);
    }

    private List<String> names() throws IOException {
        try (Stream<Path> children = Files.list(folder())) {
            return children.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    /** The top-level content of the files of a folder but the realm name, by key, wherever it is written. */
    private static Map<String, Object> contentOf(final Path folder) throws IOException {
        Map<String, Object> content = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(folder)) {
            for (Path path : paths.sorted().toList()) {
                String name = path.getFileName().toString();
                TreeObject root = DocumentTrees.parse(name, Files.readString(path, StandardCharsets.UTF_8))
                        .root();
                for (String key : root.keys()) {
                    if (!key.equals("realm")) {
                        content.merge(key, root.entry(key).orElseThrow().plain(), ReorganizeFilesTest::concatenated);
                    }
                }
            }
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private static Object concatenated(final Object left, final Object right) {
        if (left instanceof List<?> first && right instanceof List<?> second) {
            List<Object> all = new ArrayList<>((List<Object>) first);
            all.addAll(second);
            return all;
        }
        throw new AssertionError("A key is written twice");
    }

    private List<String> keysOf(final String name) throws IOException {
        return DocumentTrees.parse(name, read(name)).root().keys();
    }

    private void apply() {
        this.files.reorganize("demo", this.files.planReorganization("demo"));
    }

    @Test
    void aMonolithicYamlExportIsSplit() throws IOException {
        copy("monolithic-yaml");

        apply();

        assertThat(names())
                .containsExactly(
                        "00-realm.yaml", "20-clients.yaml", "30-roles.yaml", "40-groups.yaml", "50-links.yaml");
        assertThat(keysOf("00-realm.yaml"))
                .containsExactly(
                        "realm", "enabled", "displayName", "smtpServer", "clientScopes", "authenticationFlows");
        assertThat(keysOf("20-clients.yaml")).containsExactly("realm", "clients");
        assertThat(keysOf("30-roles.yaml")).containsExactly("realm", "roles");
        assertThat(keysOf("40-groups.yaml")).containsExactly("realm", "groups");
        assertThat(keysOf("50-links.yaml")).containsExactly("realm", "users", "defaultGroups");
        assertThat(contentOf(folder())).isEqualTo(contentOf(RESOURCES.resolve("monolithic-yaml/demo")));
        assertThat(read("20-clients.yaml")).contains("# The public single page application");
    }

    @Test
    void aMonolithicJsonExportIsSplit() throws IOException {
        copy("monolithic-json");

        apply();

        assertThat(names())
                .containsExactly(
                        "00-realm.json", "20-clients.json", "30-roles.json", "40-groups.json", "50-links.json");
        assertThat(keysOf("00-realm.json"))
                .containsExactly(
                        "realm", "enabled", "displayName", "smtpServer", "clientScopes", "authenticationFlows");
        assertThat(keysOf("20-clients.json")).containsExactly("realm", "clients");
        assertThat(keysOf("50-links.json")).containsExactly("realm", "users", "defaultGroups");
        assertThat(contentOf(folder())).isEqualTo(contentOf(RESOURCES.resolve("monolithic-json/demo")));
    }

    @Test
    void aMixedFolderJoinsTheStandardFiles() throws IOException {
        copy("mixed");

        ReorganizationPlan plan = this.files.planReorganization("demo");
        this.files.reorganize("demo", plan);

        assertThat(plan.removals()).containsExactly("my-clients.yaml");
        assertThat(names())
                .containsExactly(
                        "00-realm.json", "20-clients.json", "30-roles.yaml", "40-groups.json", "50-links.json");
        assertThat(keysOf("20-clients.json")).containsExactly("realm", "clients");
        assertThat(keysOf("40-groups.json")).containsExactly("realm", "groups");
        assertThat(keysOf("00-realm.json")).containsExactly("realm", "enabled", "displayName", "smtpServer");
        assertThat(contentOf(folder())).isEqualTo(contentOf(RESOURCES.resolve("mixed/demo")));
    }

    @Test
    void placeholdersStayQuoted() throws IOException {
        copy("monolithic-yaml");

        apply();

        for (String name : names()) {
            assertThat(read(name)).contains("realm: \"{{env}}-demo\"");
        }
        assertThat(read("20-clients.yaml")).contains("- \"{{appUrl}}/*\"");
    }

    @Test
    void placeholdersStayQuotedWhenTheFormatChanges() throws IOException {
        copy("mixed");

        apply();

        assertThat(read("20-clients.json")).contains("\"{{appUrl}}/*\"").contains("\"realm\": \"{{env}}-demo\"");
    }

    @Test
    void anAlreadyStandardFolderHasNothingToMove() throws IOException {
        copy("already-standard");

        ReorganizationPlan plan = this.files.planReorganization("demo");

        assertThat(plan.isEmpty()).isTrue();
        String before = read("20-clients.yaml");
        this.files.reorganize("demo", plan);
        assertThat(read("20-clients.yaml")).isEqualTo(before);
        assertThat(names()).hasSize(5);
    }

    @Test
    void listsInTwoFilesAreConcatenated() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("extra.yaml"), """
                realm: "{{env}}-demo"
                clients:
                  - clientId: backend
                    publicClient: false
                """);

        ReorganizationPlan plan = this.files.planReorganization("demo");
        this.files.reorganize("demo", plan);

        assertThat(plan.moves())
                .containsExactly(new Move("clients", OptionalInt.of(1), "extra.yaml", "20-clients.yaml"));
        assertThat(plan.removals()).containsExactly("extra.yaml");
        assertThat(names()).doesNotContain("extra.yaml");
        assertThat(DocumentTrees.parse("20-clients.yaml", read("20-clients.yaml")).root().objects("clients").stream()
                        .map(client -> client.text("clientId").orElseThrow()))
                .containsExactly("frontend", "backend");
    }

    @Test
    void thePlanListsCountsForLists() throws IOException {
        copy("monolithic-yaml");

        ReorganizationPlan plan = this.files.planReorganization("demo");

        assertThat(plan.renames()).containsExactly(new Rename("demo.yaml", "00-realm.yaml"));
        assertThat(plan.removals()).isEmpty();
        assertThat(plan.moves())
                .containsExactly(
                        new Move("users", OptionalInt.of(2), "demo.yaml", "50-links.yaml"),
                        new Move("clients", OptionalInt.of(2), "demo.yaml", "20-clients.yaml"),
                        new Move("roles", OptionalInt.empty(), "demo.yaml", "30-roles.yaml"),
                        new Move("groups", OptionalInt.of(2), "demo.yaml", "40-groups.yaml"),
                        new Move("defaultGroups", OptionalInt.of(1), "demo.yaml", "50-links.yaml"));
    }

    @Test
    void aPlanThatNoLongerMatchesTheFolderIsRefused() throws IOException {
        copy("monolithic-yaml");
        ReorganizationPlan plan = this.files.planReorganization("demo");
        Files.delete(folder().resolve("demo.yaml"));

        assertThatThrownBy(() -> this.files.reorganize("demo", plan))
                .isInstanceOf(ReorganizationRefusedException.class)
                .hasMessageContaining("out of date");
        assertThat(names()).isEmpty();
    }

    @Test
    void aFailingWriteDeletesNothing() throws IOException {
        copy("monolithic-yaml");
        ReorganizationPlan plan = this.files.planReorganization("demo");
        Files.createDirectories(folder().resolve("40-groups.yaml/keep"));

        assertThatThrownBy(() -> this.files.reorganize("demo", plan))
                .isInstanceOf(ReorganizationInterruptedException.class);
        assertThat(folder().resolve("demo.yaml")).exists();
    }

    @Test
    void aFailureWhilePuttingTheFilesInPlaceNamesTheReplacedFiles() throws IOException {
        copy("monolithic-yaml");
        ReorganizationPlan plan = this.files.planReorganization("demo");
        Files.createDirectories(folder().resolve("40-groups.yaml/keep"));

        assertThatThrownBy(() -> this.files.reorganize("demo", plan))
                .isInstanceOf(ReorganizationInterruptedException.class)
                .hasMessageContaining("00-realm.yaml, 50-links.yaml, 20-clients.yaml, 30-roles.yaml")
                .hasMessageContaining("restore")
                .hasMessageContaining("Git");
    }

    @Test
    void aRerunAfterAPartialFailureDuplicatesNothing() throws IOException {
        copy("monolithic-yaml");
        ReorganizationPlan plan = this.files.planReorganization("demo");
        Path blocking = folder().resolve("40-groups.yaml");
        Files.createDirectories(blocking.resolve("keep"));
        assertThatThrownBy(() -> this.files.reorganize("demo", plan))
                .isInstanceOf(ReorganizationInterruptedException.class);
        Files.delete(blocking.resolve("keep"));
        Files.delete(blocking);

        apply();

        assertThat(names())
                .containsExactly(
                        "00-realm.yaml", "20-clients.yaml", "30-roles.yaml", "40-groups.yaml", "50-links.yaml");
        assertThat(contentOf(folder())).isEqualTo(contentOf(RESOURCES.resolve("monolithic-yaml/demo")));
    }

    @Test
    void theSameElementInTwoFilesIsWrittenOnce() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("extra.json"), """
                {
                  "realm": "{{env}}-demo",
                  "clients": [
                    {
                      "clientId": "frontend",
                      "publicClient": true,
                      "redirectUris": ["{{appUrl}}/*"]
                    },
                    {
                      "clientId": "backend",
                      "nodeReRegistrationTimeout": -1
                    }
                  ],
                  "users": [
                    {
                      "username": "alice",
                      "notBefore": 7
                    }
                  ]
                }
                """);
        Files.writeString(folder().resolve("50-links.yaml"), """
                realm: "{{env}}-demo"
                users:
                  - username: alice
                    notBefore: 7
                  - username: bob
                    notBefore: 5
                defaultGroups:
                  - /team
                """);
        Files.writeString(folder().resolve("my-links.yaml"), """
                realm: "{{env}}-demo"
                users:
                  - username: bob
                    notBefore: 5
                """);

        apply();

        assertThat(DocumentTrees.parse("20-clients.yaml", read("20-clients.yaml")).root().objects("clients").stream()
                        .map(client -> client.text("clientId").orElseThrow()))
                .containsExactly("frontend", "backend");
        assertThat(DocumentTrees.parse("50-links.yaml", read("50-links.yaml")).root().objects("users").stream()
                        .map(user -> user.text("username").orElseThrow()))
                .containsExactly("alice", "bob");
    }

    @Test
    void aStandardFileNameWrittenWithAnotherCaseIsTheStandardFile() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("00-Realm.yaml"), """
                realm: "{{env}}-demo"
                displayName: Demo realm
                clients:
                  - clientId: backend
                """);
        Files.writeString(folder().resolve("20-Clients.yaml"), """
                realm: "{{env}}-demo"
                clients:
                  - clientId: frontend
                """);
        this.files = new RealmDefinitionFiles(this.root);
        Map<String, Object> before = contentOf(folder());

        ReorganizationPlan plan = this.files.planReorganization("demo");
        this.files.reorganize("demo", plan);

        assertThat(plan.renames()).isEmpty();
        assertThat(plan.removals()).isEmpty();
        assertThat(plan.moves())
                .containsExactly(new Move("clients", OptionalInt.of(1), "00-Realm.yaml", "20-Clients.yaml"));
        assertThat(names()).containsExactly("00-Realm.yaml", "20-Clients.yaml");
        assertThat(contentOf(folder())).isEqualTo(before);
    }

    @Test
    void aFileWrittenIsNeverDeletedWhateverTheCaseOfItsName() throws IOException {
        Files.createDirectories(folder());
        Path written = Files.writeString(folder().resolve("20-clients.yaml"), "realm: demo\n");

        assertThat(Reorganization.isOneOf(folder().resolve("20-Clients.yaml"), List.of(written)))
                .isTrue();
        assertThat(Reorganization.isOneOf(folder().resolve("20-clients.yaml"), List.of(written)))
                .isTrue();
        assertThat(Reorganization.isOneOf(folder().resolve("30-roles.yaml"), List.of(written)))
                .isFalse();
    }

    private static final String YAML_VALUES = """
            realm: "{{env}}-demo"
            clients:
              - clientId: backend
                publicClient: yes
                standardFlowEnabled: on
                bearerOnly: off
                consentRequired: No
                octal: 017
                hex: 0x1F
                grouped: 1_000
                huge: 123456789012345678901234567890
                ratio: 1.5
                braces: "{{{raw}}}"
            """;

    @SuppressWarnings("unchecked")
    private static Object importedClient(final Object content, final String clientId) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) content).get("clients"))
                .stream()
                        .filter(client -> clientId.equals(client.get("clientId")))
                        .findFirst()
                        .orElseThrow();
    }

    @Test
    void aMergedYamlValueMeansWhatTheImportReads() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("extra.yaml"), YAML_VALUES);
        Object expected = importedClient(new Yaml().load(YAML_VALUES), "backend");

        apply();

        Object written = importedClient(new Yaml().load(read("20-clients.yaml")), "backend");
        assertThat(written).isEqualTo(expected);
    }

    @Test
    void aYamlValueMovedToJsonMeansWhatTheImportReads() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("00-realm.json"), """
                {
                  "realm": "{{env}}-demo",
                  "enabled": true
                }
                """);
        Files.writeString(folder().resolve("my-clients.yaml"), YAML_VALUES);
        this.files = new RealmDefinitionFiles(this.root);
        Object expected = importedClient(new Yaml().load(YAML_VALUES), "backend");

        apply();

        ObjectMapper mapper = new ObjectMapper();
        Object written = importedClient(mapper.readValue(read("20-clients.json"), Object.class), "backend");
        assertThat(mapper.writeValueAsString(written)).isEqualTo(mapper.writeValueAsString(expected));
        assertThat(((Map<?, ?>) written).get("publicClient")).isEqualTo(true);
        assertThat(((Map<?, ?>) written).get("huge")).isEqualTo(new BigInteger("123456789012345678901234567890"));
    }

    @Test
    void aTopLevelKeyWrittenTwiceInAFileIsRefused() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("extra.yaml"), """
                realm: "{{env}}-demo"
                clients:
                  - clientId: one
                clients:
                  - clientId: two
                """);

        assertThatThrownBy(() -> this.files.planReorganization("demo"))
                .isInstanceOf(ReorganizationRefusedException.class)
                .hasMessageContaining("clients")
                .hasMessageContaining("extra.yaml");
    }

    @Test
    void newFilesTakeTheFormatOfTheRealmFile() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("00-realm.yaml"), "realm: \"{{env}}-demo\"\n");
        Files.writeString(folder().resolve("demo.json"), """
                {
                  "realm": "{{env}}-demo",
                  "enabled": true,
                  "displayName": "Demo",
                  "clients": [{"clientId": "frontend"}]
                }
                """);
        this.files = new RealmDefinitionFiles(this.root);

        ReorganizationPlan plan = this.files.planReorganization("demo");

        assertThat(plan.moves()).contains(new Move("clients", OptionalInt.of(1), "demo.json", "20-clients.yaml"));
    }

    @Test
    void aFailureWhilePreparingTheFilesLeavesTheFolderAsItWas() throws IOException {
        copy("monolithic-yaml");
        ReorganizationPlan plan = this.files.planReorganization("demo");
        String before = read("demo.yaml");
        Files.createDirectories(folder().resolve(".40-groups.yaml.tmp/keep"));

        assertThatThrownBy(() -> this.files.reorganize("demo", plan)).isInstanceOf(UncheckedIOException.class);
        assertThat(read("demo.yaml")).isEqualTo(before);
        assertThat(this.files.planReorganization("demo")).isEqualTo(plan);
        assertThat(names()).doesNotContain("00-realm.yaml", "20-clients.yaml");
    }

    @Test
    void aStandardFileHoldingRealmKeysKeepsItsElements() throws IOException {
        copy("already-standard");
        Files.delete(folder().resolve("00-realm.yaml"));
        Files.writeString(folder().resolve("20-clients.yaml"), """
                realm: "{{env}}-demo"
                displayName: Demo realm
                clients:
                  - clientId: frontend
                """);
        Map<String, Object> before = contentOf(folder());

        ReorganizationPlan plan = this.files.planReorganization("demo");
        this.files.reorganize("demo", plan);

        assertThat(plan.renames()).isEmpty();
        assertThat(plan.moves())
                .noneMatch(move -> move.from().equals(move.to()))
                .contains(new Move("displayName", OptionalInt.empty(), "20-clients.yaml", "00-realm.yaml"));
        assertThat(keysOf("20-clients.yaml")).containsExactly("realm", "clients");
        assertThat(keysOf("00-realm.yaml")).containsExactly("realm", "displayName");
        assertThat(contentOf(folder())).isEqualTo(before);
    }

    @Test
    void rolesInTwoFilesAreMerged() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("my-roles.yaml"), """
                realm: "{{env}}-demo"
                roles:
                  realm:
                    - name: viewer
                  client:
                    backend:
                      - name: writer
                    frontend:
                      - name: user
                """);
        Files.writeString(folder().resolve("30-roles.yaml"), """
                realm: "{{env}}-demo"
                roles:
                  realm:
                    - name: admin
                  client:
                    backend:
                      - name: reader
                """);

        apply();

        TreeObject roles = DocumentTrees.parse("30-roles.yaml", read("30-roles.yaml"))
                .root()
                .object("roles")
                .orElseThrow();
        assertThat(roles.objects("realm").stream().map(role -> role.text("name").orElseThrow()))
                .containsExactly("admin", "viewer");
        Map<String, List<TreeObject>> clients =
                roles.object("client").orElseThrow().objectLists();
        assertThat(clients.get("backend").stream().map(role -> role.text("name").orElseThrow()))
                .containsExactly("reader", "writer");
        assertThat(clients.get("frontend")).hasSize(1);
        assertThat(names()).doesNotContain("my-roles.yaml");
    }

    @Test
    void listsAreConcatenatedInTheOrderOfTheFileNames() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("10-extra.yaml"), """
                realm: "{{env}}-demo"
                clients:
                  - clientId: first
                """);

        apply();

        assertThat(DocumentTrees.parse("20-clients.yaml", read("20-clients.yaml")).root().objects("clients").stream()
                        .map(client -> client.text("clientId").orElseThrow()))
                .containsExactly("first", "frontend");
    }

    @Test
    void aFileHoldingOnlyTheRealmIsRemoved() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("empty.yaml"), "realm: \"{{env}}-demo\"\n");

        ReorganizationPlan plan = this.files.planReorganization("demo");
        this.files.reorganize("demo", plan);

        assertThat(plan.removals()).containsExactly("empty.yaml");
        assertThat(names()).doesNotContain("empty.yaml");
    }

    @Test
    void theOnlyFileOfARealmIsNeverRemoved() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("demo.yaml"), "realm: \"{{env}}-demo\"\n");
        this.files = new RealmDefinitionFiles(this.root);

        assertThat(this.files.planReorganization("demo").removals()).isEmpty();
    }

    @Test
    void anIntegerReadFromJsonStaysAnIntegerInYaml() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("extra.json"), """
                {
                  "realm": "{{env}}-demo",
                  "users": [
                    {
                      "username": "carol",
                      "attributes": 12345678901234567890
                    }
                  ]
                }
                """);

        apply();

        assertThat(read("50-links.yaml"))
                .contains("attributes: 12345678901234567890")
                .doesNotContain("E");
    }

    @Test
    void realmKeysOfEveryFileMeetInTheRealmFile() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("a.yaml"), """
                realm: "{{env}}-demo"
                displayName: Same
                supportedLocales:
                  - en
                """);
        Files.writeString(folder().resolve("b.yaml"), """
                realm: "{{env}}-demo"
                displayName: Same
                supportedLocales:
                  - fr
                """);
        this.files = new RealmDefinitionFiles(this.root);

        apply();

        TreeObject realm =
                DocumentTrees.parse("00-realm.yaml", read("00-realm.yaml")).root();
        assertThat(realm.keys()).containsExactly("realm", "displayName", "supportedLocales");
        assertThat(realm.text("displayName")).contains("Same");
        assertThat(realm.texts("supportedLocales")).containsExactly("en", "fr");
        assertThat(names()).containsExactly("00-realm.yaml");
    }

    @Test
    void differentRealmValuesInTwoFilesAreRefused() throws IOException {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("a.yaml"), """
                realm: "{{env}}-demo"
                displayName: One
                """);
        Files.writeString(folder().resolve("b.yaml"), """
                realm: "{{env}}-demo"
                displayName: Two
                """);
        this.files = new RealmDefinitionFiles(this.root);

        assertThatThrownBy(() -> this.files.planReorganization("demo"))
                .isInstanceOf(ReorganizationRefusedException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void aNullValueDoesNotBreakTheMerge() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("30-roles.yaml"), """
                realm: "{{env}}-demo"
                roles:
                  client:
                    backend:
                      - name: reader
                """);
        Files.writeString(folder().resolve("my-roles.yaml"), """
                realm: "{{env}}-demo"
                roles:
                  client:
                    backend: ~
                """);

        apply();

        assertThat(DocumentTrees.parse("30-roles.yaml", read("30-roles.yaml"))
                        .root()
                        .object("roles")
                        .orElseThrow()
                        .object("client")
                        .orElseThrow()
                        .objectLists()
                        .get("backend"))
                .hasSize(1);
    }

    @Test
    void aKeyThatCannotBeMergedIsRefused() throws IOException {
        copy("already-standard");
        Files.writeString(folder().resolve("extra.yaml"), """
                realm: "{{env}}-demo"
                displayName: Another name
                """);

        assertThatThrownBy(() -> this.files.planReorganization("demo"))
                .isInstanceOf(ReorganizationRefusedException.class)
                .hasMessageContaining("displayName");
    }
}
