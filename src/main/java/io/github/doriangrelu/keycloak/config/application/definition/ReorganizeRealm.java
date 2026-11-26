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
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import java.util.List;
import java.util.Objects;

/** Puts the files of a realm in the standard layout (ADR-008), once nothing stands against it. */
public final class ReorganizeRealm {

    private final RealmDefinitions definitions;

    public ReorganizeRealm(final RealmDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * @return what reorganizing the realm would do, without writing anything
     * @throws UnknownRealmFolderException when the folder does not exist
     * @throws ReadOnlyRealmException when a file of the realm cannot be read
     * @throws UnformattedFileException when a file of the realm is not formatted yet
     */
    public ReorganizationPlan plan(final String folder) {
        check(folder);
        return this.definitions.planReorganization(folder);
    }

    /**
     * Writes the plan, after the same checks as {@link #plan}.
     *
     * @return the files written
     */
    public List<String> apply(final String folder, final ReorganizationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        check(folder);
        return this.definitions.reorganize(folder, plan);
    }

    private void check(final String folder) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        if (realm.isReadOnly()) {
            throw new ReadOnlyRealmException(realm.unreadableFiles().getFirst());
        }
        List<String> unformatted = this.definitions.unformattedFiles(folder);
        if (!unformatted.isEmpty()) {
            throw new UnformattedFileException(unformatted.getFirst());
        }
    }
}
