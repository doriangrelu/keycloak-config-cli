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

package io.github.doriangrelu.keycloak.config.application.importation.port;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.util.Collection;
import java.util.List;

/** Where the import files come from: local files, patterns, zip archives or remote URLs. */
public interface ImportFileSource {

    /**
     * Reads and prepares the files of the given locations: location after location, each one's files sorted by name.
     * Empty files are skipped.
     *
     * @throws InvalidImportException when a location matches no file, or a file cannot be read or prepared
     */
    List<ImportFile> read(Collection<String> locations);
}
