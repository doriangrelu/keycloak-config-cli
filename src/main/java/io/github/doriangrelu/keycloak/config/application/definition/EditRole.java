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

import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits the description of a role by guided questions (ADR-008): the current description is proposed, so an empty
 * answer keeps it. The name and the owner identify the role and are never changed.
 */
public final class EditRole {

    private final RealmDefinitions definitions;
    private final SaveElement saveElement;

    public EditRole(final RealmDefinitions definitions, final SaveElement saveElement) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.saveElement = Objects.requireNonNull(saveElement, "saveElement");
    }

    /**
     * @param name the name of the role, as written
     * @param client the id, as written, of the client owning the role; empty for a realm role
     * @param given the answers already given, which are not asked; its name and client are ignored
     * @return the file written, with what the rules say about the role
     * @throws UnknownRealmFolderException when the folder is not a realm of the shell
     * @throws UnknownRoleException when no role of this owner has this name
     * @throws RuleViolationException when the role fails a required rule: nothing is written
     * @throws MissingAnswerException when an answer is missing and cannot be asked
     */
    public SaveResult edit(
            final String folder,
            final String name,
            final Optional<String> client,
            final RoleAnswers given,
            final Questions questions) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        Defined<RoleDefinition> existing = realm.roles().stream()
                .filter(defined -> defined.definition().name().raw().equals(name)
                        && defined.definition()
                                .clientId()
                                .map(TemplatedValue::raw)
                                .equals(client))
                .findFirst()
                .orElseThrow(() -> new UnknownRoleException(name, client));
        RoleDefinition current = existing.definition();
        Optional<TemplatedValue> description =
                given.description().or(() -> questions.askOptionalValue("Description", current.description()));
        RoleDefinition role = new RoleDefinition(current.name(), current.clientId(), description);
        return this.saveElement.save(folder, role, Optional.of(existing.origin()), true);
    }
}
