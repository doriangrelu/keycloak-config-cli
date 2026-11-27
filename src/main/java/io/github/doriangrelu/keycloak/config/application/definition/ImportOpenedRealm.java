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
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

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
     * @throws InvalidImportException when a rendered file is not a valid import file; the message never quotes it
     */
    public ImportReport dryRun(
            final String folder,
            final Connection connection,
            final Map<String, String> given,
            final VariableValues remembered) {
        return prepare(folder, connection, given, remembered).report();
    }

    /**
     * Renders the files of the realm and computes, without writing anything, what they would change on the target:
     * {@link #apply} then imports these very files.
     *
     * @param given the values given with the command
     * @param remembered the values of the session; the given values and the answers are added to it
     * @throws MissingVariableException when a variable has no value and no answer can be asked
     * @throws InvalidImportException when a rendered file is not a valid import file; the message never quotes it
     */
    public PreparedImport prepare(
            final String folder,
            final Connection connection,
            final Map<String, String> given,
            final VariableValues remembered) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(given, "given");
        Objects.requireNonNull(remembered, "remembered");
        Map<String, String> values = this.resolveVariables.resolve(folder, given, remembered);
        // one immutable list, given to the dry run and kept for the import
        List<ImportFile> files = List.copyOf(this.definitions.importFiles(folder, values));
        ImportReport report = safely(folder, () -> connection.dryRun(files));
        return new PreparedImport(folder, connection, files, report);
    }

    /**
     * Imports the files of the prepared import, as they were when their changes were computed.
     *
     * @throws InvalidImportException when a rendered file is not a valid import file; the message never quotes it
     */
    public ImportReport apply(final PreparedImport prepared) {
        Objects.requireNonNull(prepared, "prepared");
        return safely(prepared.folder(), () -> prepared.connection().apply(prepared.files()));
    }

    /**
     * Runs the import of rendered files: a file that cannot be parsed is told by its file and place only. The message
     * of the parser quotes the content, which may hold the value of a variable; it is neither kept as the cause nor
     * shown.
     */
    private static ImportReport safely(final String folder, final Supplier<ImportReport> importation) {
        try {
            return importation.get();
        } catch (InvalidImportException invalid) {
            throw new InvalidImportException(invalid.safeMessage()
                            .orElse("An import file of '" + folder + "' is not valid once its variables are rendered")
                    + " (the content of the file is not shown: it may hold the value of a variable)");
        }
    }
}
