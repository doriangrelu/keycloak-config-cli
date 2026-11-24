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
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.util.List;
import java.util.Objects;

/** Lists the realms of the root and opens one (ADR-008). */
public final class ReadRealms {

    private final RealmDefinitions definitions;

    public ReadRealms(final RealmDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public List<String> list() {
        return this.definitions.folders();
    }

    /** @throws UnknownRealmFolderException when no folder has this name */
    public RealmDefinition open(final String folder) {
        return this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
    }
}
