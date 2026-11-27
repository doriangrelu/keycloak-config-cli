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
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.util.List;
import java.util.Map;

/** An open connection to the Keycloak of a target, checked by a first call. Closing it releases every resource. */
public interface Connection extends AutoCloseable {

    /** The target this connection was opened to. */
    Target target();

    /** The version of the Keycloak server, read when the connection was opened. */
    String keycloakVersion();

    /**
     * Computes what importing the files would change on the target, without writing anything: a missing realm is not
     * created. The import options are those of the command-line import by default.
     *
     * @param files the import files, in the order they are applied
     * @return the changes the import would make, by realm
     * @throws io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException when a file is not
     *     a valid import file
     * @throws KeycloakUnreachableException when Keycloak cannot be reached or does not answer in time
     * @throws KeycloakAuthenticationException when Keycloak refuses the account of the target
     */
    ImportReport dryRun(List<ImportFile> files);

    /**
     * Imports the files on the target, then removes what they no longer declare, as the command-line import does.
     *
     * @param files the import files, in the order they are applied
     * @return the changes made, by realm
     * @throws io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException when a file is not
     *     a valid import file; nothing is changed then
     * @throws KeycloakUnreachableException when Keycloak cannot be reached or does not answer in time
     * @throws KeycloakAuthenticationException when Keycloak refuses the account of the target
     */
    ImportReport apply(List<ImportFile> files);

    /**
     * Reads a realm of the target as an import document: the whole realm, normalized against the reference realm of
     * the version of the target (what Keycloak sets by default is left out), its secrets replaced by placeholders.
     *
     * @param withUsers whether the users are read too, without their credentials; the users from a directory and the
     *     service accounts are left out
     * @throws RealmNotFoundOnTargetException when the target has no such realm
     * @throws KeycloakUnreachableException when Keycloak cannot be reached or does not answer in time
     * @throws KeycloakAuthenticationException when Keycloak refuses the account of the target
     */
    ExtractedRealm extract(String realm, boolean withUsers);

    /**
     * The current secrets of the confidential clients of a realm, by the name of the variable an extraction gives them
     * ({@code shopSecret}). The map never shows a value in its text.
     *
     * @throws RealmNotFoundOnTargetException when the target has no such realm
     * @throws KeycloakUnreachableException when Keycloak cannot be reached or does not answer in time
     * @throws KeycloakAuthenticationException when Keycloak refuses the account of the target
     */
    Map<String, String> clientSecrets(String realm);

    /** Closes the connection; closing it again does nothing. */
    @Override
    void close();
}
