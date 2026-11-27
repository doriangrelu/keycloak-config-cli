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

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealmImportFilesWithElementTest {

    @TempDir
    Path root;

    private Path folder;
    private RealmDefinitionFiles files;

    @BeforeEach
    void setUp() throws IOException {
        this.folder = Files.createDirectories(this.root.resolve("one").resolve("master"));
        this.files = new RealmDefinitionFiles(this.root.resolve("one"));
        write("00-realm.yaml", "realm: master\nenabled: true\n");
    }

    private void write(final String name, final String content) throws IOException {
        Files.writeString(this.folder.resolve(name), content);
    }

    private static ClientDefinition client(final String id) {
        return new ClientDefinition(
                TemplatedValue.of(id),
                Optional.empty(),
                Optional.empty(),
                ApplicationType.SERVICE,
                List.of(),
                List.of(),
                Map.of(),
                Optional.empty());
    }

    private static GroupDefinition group(final String name) {
        return new GroupDefinition("/" + name, TemplatedValue.of(name), Set.of(), Map.of());
    }

    private static String simpleName(final ImportFile file) {
        return Path.of(URI.create(file.name())).getFileName().toString();
    }

    private static List<String> view(final List<ImportFile> files) {
        return files.stream()
                .map(file -> simpleName(file) + "\n" + file.content())
                .toList();
    }

    private static ImportFile named(final List<ImportFile> files, final String name) {
        return files.stream()
                .filter(file -> simpleName(file).equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theElementIsAddedToItsFile() throws IOException {
        write("20-clients.yaml", "realm: master\nclients:\n  - clientId: existing\n    enabled: true\n");

        List<ImportFile> read = this.files.importFilesWith("master", client("added"), Map.of());

        assertThat(read)
                .extracting(RealmImportFilesWithElementTest::simpleName)
                .containsExactly("00-realm.yaml", "20-clients.yaml");
        assertThat(named(read, "20-clients.yaml").content())
                .contains("clientId: existing")
                .contains("clientId: added");
    }

    @Test
    void anElementWithoutFileGetsTheFileSaveWouldCreate() throws IOException {
        write("20-clients.yaml", "realm: master\nclients:\n  - clientId: existing\n");

        List<ImportFile> read = this.files.importFilesWith("master", group("team"), Map.of());

        assertThat(read)
                .extracting(RealmImportFilesWithElementTest::simpleName)
                .containsExactly("00-realm.yaml", "20-clients.yaml", "40-groups.yaml");
        ImportFile created = named(read, "40-groups.yaml");
        assertThat(created.content()).contains("realm: master").contains("name: team");
        assertThat(created.location()).isEqualTo(read.get(0).location());
        String directory = read.get(0).name().substring(0, read.get(0).name().lastIndexOf('/') + 1);
        assertThat(created.name()).startsWith(directory);
    }

    @Test
    void aClientRoleGoesUnderItsClient() {
        RoleDefinition role = new RoleDefinition(
                TemplatedValue.of("viewer"), Optional.of(TemplatedValue.of("frontend")), Optional.empty());

        List<ImportFile> read = this.files.importFilesWith("master", role, Map.of());

        String content = named(read, "30-roles.yaml").content();
        assertThat(content).contains("roles:").contains("client:").contains("frontend:");
        assertThat(content).contains("name: viewer");
    }

    @Test
    void nothingIsWritten() throws IOException {
        write("20-clients.yaml", "realm: master\nclients:\n  - clientId: existing\n");
        FileTime past = FileTime.fromMillis(1_000_000_000L);
        Files.setLastModifiedTime(this.folder.resolve("20-clients.yaml"), past);
        Files.setLastModifiedTime(this.folder.resolve("00-realm.yaml"), past);
        Map<String, String> before = this.files.sources("master");

        this.files.importFilesWith("master", client("added"), Map.of());
        this.files.importFilesWith("master", group("team"), Map.of());

        assertThat(this.files.sources("master")).isEqualTo(before);
        try (Stream<Path> children = Files.list(this.folder)) {
            assertThat(children.map(path -> path.getFileName().toString()).sorted())
                    .containsExactly("00-realm.yaml", "20-clients.yaml");
        }
        assertThat(Files.getLastModifiedTime(this.folder.resolve("20-clients.yaml")))
                .isEqualTo(past);
        assertThat(Files.getLastModifiedTime(this.folder.resolve("00-realm.yaml")))
                .isEqualTo(past);
    }

    @Test
    void theContentIsWhatSaveWrites() throws IOException {
        write("20-clients.yaml", "realm: master\nclients:\n  - clientId: existing\n");
        List<ElementDefinition> elements = List.of(
                client("added"),
                group("team"),
                new RoleDefinition(TemplatedValue.of("reader"), Optional.empty(), Optional.empty()),
                new RoleDefinition(
                        TemplatedValue.of("viewer"), Optional.of(TemplatedValue.of("existing")), Optional.empty()));
        int attempt = 0;
        for (ElementDefinition element : elements) {
            List<ImportFile> predicted = this.files.importFilesWith("master", element, Map.of());

            Path copyRoot = this.root.resolve("copy-" + attempt++);
            Path copy = Files.createDirectories(copyRoot.resolve("master"));
            try (Stream<Path> children = Files.list(this.folder)) {
                for (Path file : children.toList()) {
                    Files.copy(file, copy.resolve(file.getFileName()));
                }
            }
            RealmDefinitionFiles other = new RealmDefinitionFiles(copyRoot);
            other.save("master", element, Optional.empty(), ElementKeys.empty());
            List<ImportFile> actual = other.importFiles("master", Map.of());

            assertThat(view(predicted)).isEqualTo(view(actual));
        }
    }

    @Test
    void placeholdersAreRendered() throws IOException {
        write("10-realm.yaml", "realm: master\ndisplayName: \"{{title}}\"\n");

        List<ImportFile> read = this.files.importFilesWith(
                "master", client("{{prefix}}-app"), Map.of("title", "Main", "prefix", "acme"));

        assertThat(named(read, "10-realm.yaml").content()).contains("displayName: \"Main\"");
        assertThat(named(read, "20-clients.yaml").content())
                .contains("acme-app")
                .doesNotContain("{{");
    }
}
