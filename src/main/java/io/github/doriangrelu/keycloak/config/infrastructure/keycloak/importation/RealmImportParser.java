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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.RealmDocuments;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reads the realms an import file declares, each one remembering the file it comes from. */
@Component
public class RealmImportParser {

    /** @throws InvalidImportException when the file is not a valid import file */
    public List<RealmImport> parse(final ImportFile file) {
        List<RealmImport> realms = RealmDocuments.parse(file, RealmImport.class);
        realms.forEach(realm -> realm.setSource(file.name()));
        return realms;
    }
}
