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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Creates the folder of a new realm, with its realm file. */
public final class CreateRealm {

    private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9_-])?");

    private final RealmDefinitions definitions;

    public CreateRealm(final RealmDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * Checks that a folder can be created: a plain name that does not exist yet, whatever its case.
     *
     * @throws InvalidRealmFolderException when the folder is not a plain name
     * @throws RealmFolderExistsException when the folder exists already
     */
    public void checkFolder(final String folder) {
        if (folder == null || !FOLDER.matcher(folder).matches()) {
            throw new InvalidRealmFolderException(folder);
        }
        if (this.definitions.folders().stream()
                .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(folder.toLowerCase(Locale.ROOT)))) {
            throw new RealmFolderExistsException(folder);
        }
    }

    /**
     * @return the name of the realm file written, in the folder
     * @throws InvalidRealmFolderException when the folder is not a plain name
     * @throws RealmFolderExistsException when the folder exists already
     */
    public String create(final String folder, final TemplatedValue realm, final DefinitionFormat format) {
        checkFolder(folder);
        return this.definitions.createRealm(folder, realm, format);
    }
}
