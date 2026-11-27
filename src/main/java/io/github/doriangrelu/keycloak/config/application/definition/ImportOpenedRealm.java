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
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Imports the opened realm on the connected target (ADR-010): its files, rendered with the values of its variables, as
 * {@code import.files.locations=<folder>} would read them. The variables are resolved before the files are rendered,
 * so that a missing value is told in the words of the shell.
 */
public final class ImportOpenedRealm {

    private final ResolveVariables resolveVariables;
    private final RealmDefinitions definitions;

    public ImportOpenedRealm(final ResolveVariables resolveVariables, final RealmDefinitions definitions) {
        this.resolveVariables = Objects.requireNonNull(resolveVariables, "resolveVariables");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * What importing the realm would change on the target, without writing anything.
     *
     * @param given the values given with the command
     * @param remembered the values of the session; the given values and the answers are added to it
     * @throws MissingVariableException when a variable has no value and no answer can be asked
     */
    public ImportReport dryRun(
            final String folder,
            final Connection connection,
            final Map<String, String> given,
            final VariableValues remembered) {
        return connection.dryRun(files(folder, given, remembered));
    }

    /**
     * Imports the realm on the target. After a {@link #dryRun} with the same session values, the files are the same:
     * every value given or answered is remembered.
     *
     * @throws MissingVariableException when a variable has no value and no answer can be asked
     */
    public ImportReport apply(
            final String folder,
            final Connection connection,
            final Map<String, String> given,
            final VariableValues remembered) {
        return connection.apply(files(folder, given, remembered));
    }

    private List<ImportFile> files(
            final String folder, final Map<String, String> given, final VariableValues remembered) {
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(remembered, "remembered");
        Map<String, String> values = this.resolveVariables.resolve(folder, given, remembered);
        return this.definitions.importFiles(folder, values);
    }
}
