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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.doriangrelu.keycloak.config.application.definition.InvalidRealmFolderException;
import io.github.doriangrelu.keycloak.config.application.definition.RealmFolderExistsException;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class ExtractedRealmFilesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DOCUMENT = """
            {
              "realm": "demo",
              "enabled": true,
              "displayName": "Demo",
              "smtpServer": {"host": "smtp.example.org", "password": "{{smtpPassword}}"},
              "clients": [
                {"clientId": "shop", "secret": "{{shopSecret}}", "redirectUris": ["https://shop.example.org/*"]},
                {"clientId": "admin", "publicClient": true}
              ],
              "roles": {
                "realm": [{"name": "buyer"}],
                "client": {"shop": [{"name": "seller"}]}
              },
              "groups": [{"name": "staff", "path": "/staff"}],
              "defaultRole": {"name": "default-roles-demo"},
              "scopeMappings": [{"clientScope": "profile", "roles": ["buyer"]}],
              "identityProviders": [{"alias": "google", "config": {"clientSecret": "{{googleClientSecret}}"}}]
            }
            """;

    @TempDir
    Path root;

    private RealmDefinitionFiles files() {
        return new RealmDefinitionFiles(this.root);
    }

    private String read(final String folder, final String name) throws IOException {
        return Files.readString(this.root.resolve(folder).resolve(name), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Set<String> yamlKeys(final String folder, final String name) throws IOException {
        return new TreeSet<>(((Map<String, Object>) new Yaml().load(read(folder, name))).keySet());
    }

    private List<String> names(final String folder) {
        try (Stream<Path> children = Files.list(this.root.resolve(folder))) {
            return children.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aDocumentIsSplitByTheStandardLayout() throws IOException {
        List<String> written = files().createExtractedRealm("demo", DefinitionFormat.YAML, DOCUMENT);

        assertThat(written)
                .containsExactly(
                        "00-realm.yaml", "20-clients.yaml", "30-roles.yaml", "40-groups.yaml", "50-links.yaml");
        assertThat(yamlKeys("demo", "00-realm.yaml"))
                .containsExactlyInAnyOrder("realm", "enabled", "displayName", "smtpServer", "identityProviders");
        assertThat(yamlKeys("demo", "20-clients.yaml")).containsExactlyInAnyOrder("realm", "clients");
        assertThat(yamlKeys("demo", "30-roles.yaml")).containsExactlyInAnyOrder("realm", "roles");
        assertThat(yamlKeys("demo", "40-groups.yaml")).containsExactlyInAnyOrder("realm", "groups");
        assertThat(yamlKeys("demo", "50-links.yaml"))
                .containsExactlyInAnyOrder("realm", "defaultRole", "scopeMappings");
    }

    @Test
    void theUnionOfTheFilesIsTheDocument() throws IOException {
        List<String> written = files().createExtractedRealm("demo", DefinitionFormat.JSON, DOCUMENT);

        ObjectNode merged = MAPPER.createObjectNode();
        for (String name : written) {
            JsonNode file = MAPPER.readTree(read("demo", name));
            file.fields().forEachRemaining(field -> {
                if (!field.getKey().equals("realm")) {
                    assertThat(merged.has(field.getKey())).isFalse();
                    merged.set(field.getKey(), field.getValue());
                }
            });
            assertThat(file.get("realm").asText()).isEqualTo("demo");
        }
        merged.put("realm", "demo");
        assertThat(merged).isEqualTo(MAPPER.readTree(DOCUMENT));
    }

    @Test
    void jsonFormatWritesJsonFiles() throws IOException {
        List<String> written = files().createExtractedRealm("demo", DefinitionFormat.JSON, DOCUMENT);

        assertThat(written).allMatch(name -> name.endsWith(".json"));
        Set<String> keys = new TreeSet<>();
        MAPPER.readTree(read("demo", "20-clients.json")).fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("clients", "realm");
    }

    @Test
    void placeholdersAreQuoted() throws IOException {
        files().createExtractedRealm("demo", DefinitionFormat.YAML, DOCUMENT);

        assertThat(read("demo", "20-clients.yaml")).contains("secret: \"{{shopSecret}}\"");
        assertThat(read("demo", "00-realm.yaml")).contains("password: \"{{smtpPassword}}\"");
        assertThat(files().unformattedFiles("demo")).isEmpty();
    }

    @Test
    void anExistingFolderIsRefusedAndUntouched() throws IOException {
        Files.createDirectories(this.root.resolve("demo"));
        Files.writeString(this.root.resolve("demo").resolve("keep.yaml"), "realm: demo\n");

        assertThatThrownBy(() -> files().createExtractedRealm("demo", DefinitionFormat.YAML, DOCUMENT))
                .isInstanceOf(RealmFolderExistsException.class);

        assertThat(names("demo")).containsExactly("keep.yaml");
        assertThat(read("demo", "keep.yaml")).isEqualTo("realm: demo\n");
    }

    @Test
    void aFolderDifferingByCaseIsRefused() throws IOException {
        Files.createDirectories(this.root.resolve("Demo"));

        assertThatThrownBy(() -> files().createExtractedRealm("demo", DefinitionFormat.YAML, DOCUMENT))
                .isInstanceOf(RealmFolderExistsException.class);

        assertThat(names("Demo")).isEmpty();
        assertThat(files().folders()).containsExactly("Demo");
    }

    @Test
    void anInvalidFolderIsRefused() {
        assertThatThrownBy(() -> files().createExtractedRealm("../out", DefinitionFormat.YAML, DOCUMENT))
                .isInstanceOf(InvalidRealmFolderException.class);
    }

    @Test
    void aFailureLeavesNothing() {
        assertThatThrownBy(() -> files().createExtractedRealm("demo", DefinitionFormat.YAML, "{ not json"))
                .isInstanceOf(RuntimeException.class);

        assertThat(files().folders()).isEmpty();
        assertThat(names(".")).isEmpty();
    }

    @Test
    void typesWithoutContentAreNotWritten() {
        String document = """
                {"realm": "demo", "enabled": true, "clients": [{"clientId": "shop"}], "groups": [], "roles": {}}
                """;

        List<String> written = files().createExtractedRealm("demo", DefinitionFormat.YAML, document);

        assertThat(written).containsExactly("00-realm.yaml", "20-clients.yaml");
        assertThat(names("demo")).containsExactly("00-realm.yaml", "20-clients.yaml");
    }

    @Test
    void theFolderOpensAsARealm() {
        files().createExtractedRealm("demo", DefinitionFormat.YAML, DOCUMENT);

        RealmDefinition realm = files().load("demo").orElseThrow();

        assertThat(realm.realm().orElseThrow().raw()).isEqualTo("demo");
        assertThat(realm.clients()).hasSize(2);
        assertThat(realm.roles()).hasSize(2);
        assertThat(realm.groups()).hasSize(1);
        assertThat(realm.unreadableFiles()).isEmpty();
    }
}
