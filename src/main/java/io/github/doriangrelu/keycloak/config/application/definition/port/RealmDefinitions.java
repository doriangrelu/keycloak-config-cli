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

package io.github.doriangrelu.keycloak.config.application.definition.port;

import io.github.doriangrelu.keycloak.config.application.definition.RealmFolderExistsException;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The definitions of the realms: one folder per realm under a root (ADR-008). */
public interface RealmDefinitions {

    /** The folders of the realms, sorted. */
    List<String> folders();

    /** Every file of the folder of a realm, read before rendering; empty when the folder does not exist. */
    Optional<RealmDefinition> load(String folder);

    /** The files of a realm that are not in the layout of keycloak-config-cli yet: writing them would change them. */
    List<String> unformattedFiles(String folder);

    /**
     * Writes every file of a realm in the layout of keycloak-config-cli, until it is stable, without changing its
     * content.
     *
     * @return the files changed
     */
    List<String> format(String folder);

    /** The file of a realm that holds the elements of a type: the first one, by name, that declares it. */
    Optional<String> fileOf(String folder, ElementType type);

    /**
     * Writes an element: in its file when it exists, else in the file that holds its type, else in the standard
     * numbered file of its type, created with the {@code realm} key of the realm. Only the keys the editing model
     * manages are written, then the keys {@code added} that are not written yet: the other keys of the element stay as
     * they are.
     *
     * @param existing where the element is written, when it exists
     * @param added the keys the rules add, written after the keys of the model
     * @return the file written
     */
    String save(String folder, ElementDefinition definition, Optional<Origin> existing, ElementKeys added);

    /**
     * The keys of an element as {@link #save} would write them, without writing anything.
     *
     * @param existing where the element is written, when it exists
     * @param added the keys the rules add
     */
    ElementKeys preview(String folder, ElementDefinition definition, Optional<Origin> existing, ElementKeys added);

    /** The keys of each element read, by origin: the same origins as {@link #load}. */
    Map<Origin, ElementKeys> elementKeys(String folder);

    /**
     * The top-level keys of all the files of a realm, without the elements ({@code clients}, {@code roles},
     * {@code groups}) and the other lists of objects.
     */
    ElementKeys realmKeys(String folder);

    /**
     * Creates the folder of a new realm and its {@code 00-realm} file, with the {@code realm} key and {@code enabled},
     * then the keys {@code added}.
     *
     * @return the name of the file written
     */
    String createRealm(String folder, TemplatedValue realm, DefinitionFormat format, ElementKeys added);

    /**
     * Creates the folder of a realm extracted from Keycloak, split by the standard layout: {@code 00-realm} with the
     * keys that are not elements, {@code 20-clients}, {@code 30-roles}, {@code 40-groups} and {@code 50-links}, each
     * with the {@code realm} key; a type without content has no file. Every file is computed in memory, then written:
     * on a failure nothing remains.
     *
     * @param document the realm in the import format of Keycloak, as JSON, with its placeholders
     * @return the names of the files written, in the order of the standard files
     * @throws io.github.doriangrelu.keycloak.config.application.definition.InvalidRealmFolderException when the
     *     folder is not a plain name
     * @throws RealmFolderExistsException when the folder exists already, whatever its case
     */
    List<String> createExtractedRealm(String folder, DefinitionFormat format, String document);

    /** The text of each definition file of a realm, as written (comments included), by file name. */
    Map<String, String> sources(String folder);

    /**
     * The text of each definition file of a realm as {@link #sources} gives it, after {@code save(folder, element,
     * Optional.empty(), ElementKeys.empty())}: the file of the element holds it (or is added, as {@code save} would
     * create it). Computed in memory: nothing is written.
     */
    Map<String, String> sourcesWith(String folder, ElementDefinition element);

    /** What reorganizing the files of a realm into the standard layout would do, without writing anything. */
    ReorganizationPlan planReorganization(String folder);

    /**
     * Writes the plan: the keys moved to their standard file, the files renamed, the empty files removed.
     *
     * @return the files written
     */
    List<String> reorganize(String folder, ReorganizationPlan plan);

    /**
     * The files of a realm folder, read as the import reads {@code import.files.locations=<folder>} (same order, hidden
     * files ignored), with their placeholders rendered: the {@code values} first, then the default values of the
     * placeholders. Nothing is written.
     *
     * @param values the explicit value of each variable; a value never appears in an error message
     * @throws io.github.doriangrelu.keycloak.config.application.definition.UnknownRealmFolderException when the folder
     *     is not a realm of the shell
     * @throws InvalidImportException when a file cannot be read or a placeholder has no value
     */
    List<ImportFile> importFiles(String folder, Map<String, String> values);

    /**
     * The files the import of a realm folder would read after {@code save(folder, element, Optional.empty(),
     * ElementKeys.empty())}: same order, rendering and errors as {@link #importFiles}, the element written in its file
     * (or in the file {@code save} would create). Computed in memory: nothing is written, no file is created.
     *
     * @param values the explicit value of each variable; a value never appears in an error message
     */
    List<ImportFile> importFilesWith(String folder, ElementDefinition element, Map<String, String> values);
}
