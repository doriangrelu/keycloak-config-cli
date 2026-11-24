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

import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.util.List;
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
     * manages are written: the other keys of the element stay as they are.
     *
     * @param existing where the element is written, when it exists
     * @return the file written
     */
    String save(String folder, ElementDefinition definition, Optional<Origin> existing);
}
