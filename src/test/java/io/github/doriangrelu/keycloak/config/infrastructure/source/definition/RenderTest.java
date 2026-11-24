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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class RenderTest {

    private static final Pattern COMMENT = Pattern.compile("(?:^|\\s)(#.*)$", Pattern.MULTILINE);

    @TempDir
    Path root;

    private Path realm;
    private RealmDefinitionFiles files;

    @BeforeEach
    void setUp() throws IOException {
        this.realm = Files.createDirectories(this.root.resolve("demo"));
        this.files = new RealmDefinitionFiles(this.root);
    }

    private String resource(final String name) throws IOException {
        try (InputStream input = RenderTest.class.getResourceAsStream("/definition/render/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String file(final String name) throws IOException {
        return Files.readString(this.realm.resolve(name), StandardCharsets.UTF_8);
    }

    private static List<String> comments(final String content) {
        Matcher matcher = COMMENT.matcher(content);
        return matcher.results().map(result -> result.group(1).trim()).toList();
    }

    @Test
    void aFormattedFileIsStable() throws IOException {
        String original = resource("20-clients.yaml");
        Files.writeString(this.realm.resolve("20-clients.yaml"), original, StandardCharsets.UTF_8);

        assertThat(this.files.unformattedFiles("demo")).containsExactly("20-clients.yaml");
        assertThat(this.files.format("demo")).containsExactly("20-clients.yaml");

        String formatted = file("20-clients.yaml");
        assertThat(DocumentTrees.parse("20-clients.yaml", formatted).render()).isEqualTo(formatted);
        assertThat(this.files.unformattedFiles("demo")).isEmpty();
        assertThat(this.files.format("demo")).isEmpty();
        assertThat(comments(formatted)).containsExactlyElementsOf(comments(original));
    }

    @Test
    void formatDoesNotChangeTheContent() throws IOException {
        String yaml = resource("20-clients.yaml");
        String json = resource("20-clients.json");
        Files.writeString(this.realm.resolve("20-clients.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(this.realm.resolve("21-clients.json"), json, StandardCharsets.UTF_8);

        this.files.format("demo");

        assertThat((Object) new Yaml().load(file("20-clients.yaml"))).isEqualTo(new Yaml().load(yaml));
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readTree(file("21-clients.json"))).isEqualTo(mapper.readTree(json));
        assertThat(file("20-clients.yaml")).contains("\"{{appUrl}}/*\"");
    }

    @Test
    void lineEndingsAndAccentsAreKept() throws IOException {
        String crlf = "realm: demo\r\nclients:\r\n  - clientId: app\r\n    description: \"Équipe sécurité\"\r\n";
        Files.writeString(this.realm.resolve("20-clients.yaml"), crlf, StandardCharsets.UTF_8);

        this.files.format("demo");

        String formatted = file("20-clients.yaml");
        assertThat(formatted).contains("\r\n").contains("Équipe sécurité");
        assertThat(formatted.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void jsonArraysAreOnePerLine() throws IOException {
        Files.writeString(
                this.realm.resolve("20-clients.json"),
                "{\"clients\": [{\"clientId\": \"app\", \"redirectUris\": [\"a\", \"b\"]}]}",
                StandardCharsets.UTF_8);

        this.files.format("demo");

        assertThat(file("20-clients.json").lines().map(String::trim))
                .contains("\"a\",", "\"b\"", "\"clientId\": \"app\",");
        assertThat(file("20-clients.json")).endsWith("}\n");
    }
}
