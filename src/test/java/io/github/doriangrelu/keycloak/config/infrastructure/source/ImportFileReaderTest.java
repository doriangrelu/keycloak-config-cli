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

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ImportFileReaderTest {

    @TempDir
    Path directory;

    private final FileLocations locations =
            new FileLocations(new PathMatchingResourcePatternResolver(), List.of(), false);

    private String file(final String content) throws IOException {
        return Files.writeString(this.directory.resolve("realm.json"), content)
                .toUri()
                .toString();
    }

    @Test
    void filesAreRenderedAsTemplatesWhenTemplatingIsEnabled() throws IOException {
        String location = file("{\"realm\": \"{{realm}}\"}");
        ImportFileReader reader =
                new ImportFileReader(this.locations, Optional.of(new MustacheTemplates(Map.of("realm", "demo"))));

        List<ImportFile> files = reader.read(List.of(location));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.content()).isEqualTo("{\"realm\": \"demo\"}");
            assertThat(file.location()).isEqualTo(location);
            assertThat(file.name()).endsWith("realm.json");
        });
    }

    @Test
    void filesAreLeftAsTheyAreWhenTemplatingIsDisabled() throws IOException {
        String location = file("{\"displayName\": \"{{not a variable}}\"}");
        ImportFileReader reader = new ImportFileReader(this.locations, Optional.empty());

        assertThat(reader.read(List.of(location)))
                .extracting(ImportFile::content)
                .containsExactly("{\"displayName\": \"{{not a variable}}\"}");
    }
}
