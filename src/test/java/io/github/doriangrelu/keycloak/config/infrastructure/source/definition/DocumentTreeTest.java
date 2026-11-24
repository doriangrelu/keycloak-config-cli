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

import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTreeTest {

    private static DocumentTree read(final String file) throws IOException {
        try (InputStream input = DocumentTreeTest.class.getResourceAsStream("/definition/read/" + file)) {
            return DocumentTrees.parse(file, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @CsvSource({"clients.yaml, YAML, 6, 12", "clients.json, JSON, 4, 12"})
    void readsTextsBooleansListsAndObjects(
            final String file, final DefinitionFormat format, final int frontendLine, final int batchLine)
            throws IOException {
        DocumentTree tree = read(file);
        List<TreeObject> clients = tree.root().objects("clients");

        assertThat(tree.format()).isEqualTo(format);
        assertThat(clients).hasSize(2);
        TreeObject frontend = clients.get(0);
        assertThat(frontend.text("clientId")).contains("frontend");
        assertThat(frontend.bool("publicClient")).contains(true);
        assertThat(frontend.bool("standardFlowEnabled")).isEmpty();
        assertThat(frontend.texts("redirectUris")).containsExactly("{{appUrl}}/*");
        assertThat(frontend.textEntries("attributes")).containsEntry("pkce.code.challenge.method", "S256");
        assertThat(frontend.has("redirectUris")).isTrue();
        assertThat(frontend.line()).isEqualTo(frontendLine);
        assertThat(clients.get(1).line()).isEqualTo(batchLine);
        assertThat(tree.root().object("roles").flatMap(roles -> roles.object("client")))
                .map(client -> client.objectLists().get("frontend"))
                .hasValueSatisfying(
                        viewers -> assertThat(viewers.get(0).text("name")).contains("viewer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"clients.yaml", "clients.json"})
    void placeholdersAreReadAsTheyAreWritten(final String file) throws IOException {
        assertThat(read(file).root().text("realm")).contains("{{env}}-mon-realm");
    }

    @Test
    void anUnquotedPlaceholderMakesAYamlFileUnreadable() {
        assertThatThrownBy(() -> DocumentTrees.parse("00-realm.yaml", "realm: demo\nnotBefore: {{{notBefore}}}\n"))
                .isInstanceOfSatisfying(UnreadableDocumentException.class, error -> {
                    assertThat(error.line()).isEqualTo(2);
                    assertThat(error.reason()).isNotBlank();
                });
        assertThatThrownBy(() -> DocumentTrees.parse("20-clients.yaml", "clients:\n  - clientId: {{id}}\n"))
                .isInstanceOfSatisfying(
                        UnreadableDocumentException.class,
                        error -> assertThat(error.line()).isEqualTo(2));
    }

    @Test
    void anUnquotedPlaceholderMakesAJsonFileUnreadable() {
        assertThatThrownBy(() -> DocumentTrees.parse("00-realm.json", "{\n  \"notBefore\": {{{notBefore}}}\n}\n"))
                .isInstanceOfSatisfying(
                        UnreadableDocumentException.class,
                        error -> assertThat(error.line()).isEqualTo(2));
    }

    @Test
    void placeholdersInCommentsOrQuotesDoNotMakeAFileUnreadable() {
        DocumentTree tree =
                DocumentTrees.parse("00-realm.yaml", "# set {{env}} at import\nrealm: '{{env}}-x' # {{env}}\n");

        assertThat(tree.root().text("realm")).contains("{{env}}-x");
    }

    @Test
    void anInvalidDocumentIsUnreadable() {
        assertThatThrownBy(() -> DocumentTrees.parse("00-realm.yaml", "realm: [demo\n"))
                .isInstanceOf(UnreadableDocumentException.class);
        assertThatThrownBy(() -> DocumentTrees.parse("00-realm.json", "{ \"realm\": "))
                .isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void anEmptyFileHasAnEmptyRoot() {
        assertThat(DocumentTrees.parse("empty.yaml", "").root().has("realm")).isFalse();
    }
}
