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

package io.github.doriangrelu.keycloak.config.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTest {

    @TempDir
    Path directory;

    private File create(String relativePath) throws IOException {
        Path file = this.directory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "{}").toFile();
    }

    @Test
    void aDotFileIsHiddenOnEveryOperatingSystem() throws IOException {
        assertThat(FileUtils.isHidden(create(".3_update_realm.json"))).isTrue();
        assertThat(FileUtils.isHidden(create("3_update_realm.json"))).isFalse();
    }

    @Test
    void aFileInADotDirectoryHasAHiddenAncestor() throws IOException {
        assertThat(FileUtils.hasHiddenAncestorDirectory(create(".hidden/realm.json")))
                .isTrue();
        assertThat(FileUtils.hasHiddenAncestorDirectory(create("visible/realm.json")))
                .isFalse();
    }

    @Test
    void anAbsolutePathOutsideTheWorkingDirectoryIsNotHidden() throws IOException {
        // The root of a Windows drive carries the hidden attribute: it must not hide every file below it
        File file = create("realms/realm.json");

        assertThat(file.isAbsolute()).isTrue();
        assertThat(FileUtils.hasHiddenAncestorDirectory(file)).isFalse();
    }

    @Test
    void theCurrentAndParentDirectoriesAreNotHidden() {
        assertThat(FileUtils.isHidden(new File("."))).isFalse();
        assertThat(FileUtils.isHidden(new File(".."))).isFalse();
        assertThat(FileUtils.hasHiddenAncestorDirectory(new File("./realm-config/../realm-config/realm.json")))
                .isFalse();
    }
}
