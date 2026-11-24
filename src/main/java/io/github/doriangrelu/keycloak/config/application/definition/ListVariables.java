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
import io.github.doriangrelu.keycloak.config.domain.definition.VariableUse;
import io.github.doriangrelu.keycloak.config.domain.definition.Variables;
import java.util.List;
import java.util.Objects;

/** Lists the variables of a realm, with their default values and the files that use them. */
public final class ListVariables {

    private final RealmDefinitions definitions;

    public ListVariables(final RealmDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * @return the variables, sorted by name
     * @throws UnknownRealmFolderException when the folder is not a realm
     */
    public List<VariableUse> list(final String folder) {
        if (this.definitions.load(folder).isEmpty()) {
            throw new UnknownRealmFolderException(folder);
        }
        return Variables.of(this.definitions.sources(folder));
    }
}
