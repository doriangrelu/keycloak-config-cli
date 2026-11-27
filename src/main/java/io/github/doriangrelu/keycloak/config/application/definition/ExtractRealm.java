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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Extracts a realm of the connected target into a new realm folder (ADR-010): the folder is checked before Keycloak is
 * called, the realm is read, then written all at once, then the current secrets of its confidential clients are read,
 * to be kept in the memory of the session. No secret value is ever written.
 */
public final class ExtractRealm {

    private final RealmDefinitions definitions;
    private final CreateRealm createRealm;

    public ExtractRealm(final RealmDefinitions definitions, final CreateRealm createRealm) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.createRealm = Objects.requireNonNull(createRealm, "createRealm");
    }

    /**
     * @param folder the folder to write, the name of the realm when empty
     * @throws InvalidRealmFolderException when the folder is not a plain name; Keycloak is not called
     * @throws RealmFolderExistsException when the folder exists already, whatever its case; Keycloak is not called
     * @throws RealmNotFoundOnTargetException when the target has no such realm; nothing is written
     * @throws KeycloakUnreachableException when Keycloak cannot be reached; nothing is written
     */
    public Extraction extract(
            final String realm,
            final Optional<String> folder,
            final DefinitionFormat format,
            final boolean withUsers,
            final Connection connection) {
        String chosen = folder.orElse(realm);
        this.createRealm.checkFolder(chosen);
        ExtractedRealm extracted = connection.extract(realm, withUsers);
        List<String> files = this.definitions.createExtractedRealm(chosen, format, extracted.document());
        List<String> warnings = new ArrayList<>(extracted.warnings());
        Map<String, String> kept = new TreeMap<>();
        try {
            connection.clientSecrets(realm).forEach((name, value) -> {
                if (extracted.variables().contains(name)) {
                    kept.put(name, value);
                }
            });
        } catch (RuntimeException unreadable) {
            // the message is not shown: the extraction stays, the secrets are given at import
            kept.clear();
            warnings.add("The client secrets could not be read from %s: give them at import"
                    .formatted(connection.target().name()));
        }
        List<String> toGive = extracted.variables().stream()
                .filter(name -> !kept.containsKey(name))
                .toList();
        return new Extraction(
                chosen, files, toGive, !kept.isEmpty(), warnings, extracted.skippedUsers(), ClientSecrets.of(kept));
    }

    /**
     * What an extraction wrote.
     *
     * @param folder the folder written
     * @param files the files written, in the order of the standard layout
     * @param variablesToGive the variables of the files whose value the session does not know, sorted
     * @param clientSecretsKept whether secrets of clients were read, to be kept in the memory of the session
     * @param warnings what the normalization found, then whether the secrets of the clients could not be read
     * @param skippedUsers the users from a directory and the service accounts, which are not extracted
     * @param clientSecrets the secrets of the clients to keep in the memory of the session, by variable; never written
     *     nor shown
     */
    public record Extraction(
            String folder,
            List<String> files,
            List<String> variablesToGive,
            boolean clientSecretsKept,
            List<String> warnings,
            int skippedUsers,
            ClientSecrets clientSecrets) {

        public Extraction {
            Objects.requireNonNull(folder, "folder");
            files = List.copyOf(files);
            variablesToGive = variablesToGive.stream().sorted().toList();
            warnings = List.copyOf(warnings);
            Objects.requireNonNull(clientSecrets, "clientSecrets");
        }

        /** Without any value. */
        @Override
        public String toString() {
            return "Extraction[folder=" + this.folder + ", files=" + this.files + ", variablesToGive="
                    + this.variablesToGive + ", clientSecretsKept=" + this.clientSecretsKept + ", warnings="
                    + this.warnings.size() + ", skippedUsers=" + this.skippedUsers + "]";
        }
    }
}
