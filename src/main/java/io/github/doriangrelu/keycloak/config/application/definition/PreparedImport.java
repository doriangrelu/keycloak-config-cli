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

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import java.util.List;
import java.util.Objects;

/**
 * An import of the opened realm, computed and not applied yet: the rendered files, and the changes they make on the
 * target. Applying it imports these very files, whatever the folder became since.
 *
 * @param folder the folder of the realm
 * @param connection the target the changes were computed on
 * @param files the files rendered with the values of the variables
 * @param report the changes the files make on the target
 */
public record PreparedImport(String folder, Connection connection, List<ImportFile> files, ImportReport report) {

    public PreparedImport {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(connection, "connection");
        files = List.copyOf(files);
        Objects.requireNonNull(report, "report");
    }

    /** Shows no file content: the rendered files may hold secrets. */
    @Override
    public String toString() {
        return "PreparedImport[" + this.folder + ", " + this.files.size() + " file(s), target "
                + this.connection.target().name() + "]";
    }
}
