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
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.doriangrelu.keycloak.config.application.definition.UnknownRealmFolderException;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCode;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealmImportFilesTest {

    @TempDir
    Path root;

    private Path folder;
    private RealmDefinitionFiles files;

    @BeforeEach
    void setUp() throws IOException {
        this.folder = Files.createDirectory(this.root.resolve("master"));
        this.files = new RealmDefinitionFiles(this.root);
    }

    private void write(final String name, final String content) throws IOException {
        Files.writeString(this.folder.resolve(name), content);
    }

    @Test
    void filesAreReadInNameOrder() throws IOException {
        write("20-clients.yaml", "realm: master\n");
        write("00-realm.yaml", "realm: master\n");
        write("10-roles.json", "{\"realm\":\"master\"}");

        List<ImportFile> read = this.files.importFiles("master", Map.of());

        assertThat(read)
                .extracting(file ->
                        Path.of(java.net.URI.create(file.name())).getFileName().toString())
                .containsExactly("00-realm.yaml", "10-roles.json", "20-clients.yaml");
    }

    @Test
    void placeholdersAreRenderedWithTheValues() throws IOException {
        write("00-realm.yaml", "realm: master\ndisplayName: \"{{title}}\"\n");

        List<ImportFile> read = this.files.importFiles("master", Map.of("title", "Main realm"));

        assertThat(read)
                .singleElement()
                .satisfies(
                        file -> assertThat(file.content()).isEqualTo("realm: master\ndisplayName: \"Main realm\"\n"));
    }

    @Test
    void defaultValuesApplyWhenNoValueIsGiven() throws IOException {
        write("00-realm.yaml", "realm: master\ndisplayName: \"{{title:Fallback}}\"\nloginTheme: \"{{theme:base}}\"\n");

        List<ImportFile> read = this.files.importFiles("master", Map.of("theme", "custom"));

        assertThat(read)
                .singleElement()
                .satisfies(file -> assertThat(file.content())
                        .isEqualTo("realm: master\ndisplayName: \"Fallback\"\nloginTheme: \"custom\"\n"));
    }

    @Test
    void aValueIsEscapedAsInTheImport() throws IOException {
        write("00-realm.yaml", "realm: master\ndisplayName: \"{{title}}\"\n");
        write("10-realm.json", "{\"realm\":\"master\",\"displayName\":\"{{title}}\"}");

        List<ImportFile> read = this.files.importFiles("master", Map.of("title", "say \"hi\"\nnow"));

        assertThat(read.get(0).content()).isEqualTo("realm: master\ndisplayName: \"say \\\"hi\\\"\\nnow\"\n");
        assertThat(read.get(1).content()).isEqualTo("{\"realm\":\"master\",\"displayName\":\"say \\\"hi\\\"\\nnow\"}");
    }

    @Test
    void anUndefinedVariableFailsWithCode2() throws IOException {
        write("00-realm.yaml", "realm: master\nsecret: \"{{clientSecret}}\"\nname: \"{{other}}\"\n");

        Throwable failure = catchThrowable(() -> this.files.importFiles("master", Map.of("other", "s3cr3t-value")));

        assertThat(failure)
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("clientSecret")
                .hasMessageNotContaining("s3cr3t-value");
        assertThat(ExitCodeResolver.resolve(failure)).isEqualTo(ExitCode.INVALID_CONFIGURATION);
    }

    @Test
    void anUnknownFolderIsRefused() {
        assertThatThrownBy(() -> this.files.importFiles("absent", Map.of()))
                .isInstanceOf(UnknownRealmFolderException.class);
    }

    @Test
    void hiddenFilesAreIgnored() throws IOException {
        write("00-realm.yaml", "realm: master\n");
        write(".hidden.yaml", "realm: hidden\n");

        assertThat(this.files.importFiles("master", Map.of())).hasSize(1);
    }
}
