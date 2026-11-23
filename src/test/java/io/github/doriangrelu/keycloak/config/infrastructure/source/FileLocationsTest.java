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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FileLocationsTest {

    @TempDir
    Path directory;

    private static FileLocations locations(final List<String> excludes, final boolean includeHiddenFiles) {
        return new FileLocations(new PathMatchingResourcePatternResolver(), excludes, includeHiddenFiles);
    }

    private String write(final String name, final String content) throws IOException {
        Path file = this.directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file.toUri().toString();
    }

    private String pattern() {
        return this.directory.toUri() + "**";
    }

    @Test
    void theFilesOfALocationAreSortedByNameAndRememberTheirLocation() throws IOException {
        write("2_b.json", "{\"realm\": \"b\"}");
        write("1_a.yaml", "realm: a");

        List<ImportFile> files = locations(List.of(), false).read(List.of(pattern()));

        assertThat(files)
                .extracting(ImportFile::name)
                .satisfiesExactly(
                        name -> assertThat(name).endsWith("1_a.yaml"),
                        name -> assertThat(name).endsWith("2_b.json"));
        assertThat(files).extracting(ImportFile::location).containsOnly(pattern());
        assertThat(files.getFirst().content()).isEqualTo("realm: a");
    }

    @Test
    void locationsAreReadInTheirOrder() throws IOException {
        String second = write("a.json", "{}");
        String first = write("b.json", "{}");

        List<ImportFile> files = locations(List.of(), false).read(List.of(first, second));

        assertThat(files).extracting(ImportFile::location).containsExactly(first, second);
    }

    @Test
    void emptyFilesAndDirectoriesAreSkipped() throws IOException {
        write("empty.json", "");
        write("sub/realm.json", "{}");

        List<ImportFile> files = locations(List.of(), false).read(List.of(pattern()));

        assertThat(files)
                .extracting(ImportFile::name)
                .singleElement()
                .asString()
                .endsWith("sub/realm.json");
    }

    @Test
    void hiddenFilesAreSkippedUnlessIncluded() throws IOException {
        write(".hidden.json", "{}");
        write(".hidden-directory/realm.json", "{}");
        write("visible.json", "{}");

        assertThat(locations(List.of(), false).read(List.of(pattern())))
                .extracting(ImportFile::name)
                .singleElement()
                .asString()
                .endsWith("visible.json");
        assertThat(locations(List.of(), true).read(List.of(pattern()))).hasSize(3);
    }

    @Test
    void excludedFilesAreSkipped() throws IOException {
        write("realm.json", "{}");
        write("clients/excluded.json", "{}");

        List<ImportFile> files = locations(List.of("**/clients/**"), false).read(List.of(pattern()));

        assertThat(files)
                .extracting(ImportFile::name)
                .singleElement()
                .asString()
                .endsWith("realm.json");
    }

    @Test
    void aLocationWithoutFileIsInvalid() {
        assertThatThrownBy(() -> locations(List.of(), false).read(List.of(pattern())))
                .isInstanceOf(InvalidImportException.class)
                .hasMessage("No files matching '" + pattern() + "'!");
    }

    @Test
    void aMissingFileIsInvalid() {
        String missing = this.directory.resolve("missing.json").toUri().toString();

        assertThatThrownBy(() -> locations(List.of(), false).read(List.of(missing)))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageStartingWith("Unable to proceed resource");
    }
}
