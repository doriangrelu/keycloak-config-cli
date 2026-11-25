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
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates a realm role or a client role by guided questions (ADR-008). Only the clients defined in the folder are
 * offered: the clients Keycloak creates itself are not.
 */
public final class CreateRole {

    private final RealmDefinitions definitions;
    private final SaveElement saveElement;

    public CreateRole(final RealmDefinitions definitions, final SaveElement saveElement) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.saveElement = Objects.requireNonNull(saveElement, "saveElement");
    }

    /**
     * @param given the answers already given, which are not asked
     * @return the file written, with what the rules say about the role
     * @throws UnknownRealmFolderException when the folder is not a realm of the shell
     * @throws UnknownClientException when the given client is not defined in the folder
     * @throws RoleExistsException when the given name is taken at the same place
     * @throws FirstDeclarationException when the role is the first of its place and this is not confirmed
     * @throws RuleViolationException when the role fails a required rule: nothing is written
     * @throws MissingAnswerException when an answer is missing and cannot be asked
     */
    public SaveResult create(final String folder, final RoleAnswers given, final Questions questions) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        Optional<TemplatedValue> client = owner(realm, given, questions);
        TemplatedValue name = name(realm, client, given, questions);
        Optional<TemplatedValue> description =
                given.description().or(() -> questions.askOptionalValue("Description", Optional.empty()));
        RoleDefinition role = new RoleDefinition(name, client, description);
        boolean declareType = given.declareType()
                || realm.declares(role)
                || questions.confirm(FirstDeclarationException.warning(role), false);
        return this.saveElement.save(folder, role, Optional.empty(), declareType);
    }

    private static Optional<TemplatedValue> owner(
            final RealmDefinition realm, final RoleAnswers given, final Questions questions) {
        if (given.client().isPresent()) {
            String clientId = given.client().get().raw();
            return Optional.of(realm.client(clientId)
                    .orElseThrow(() -> new UnknownClientException(clientId))
                    .definition()
                    .clientId());
        }
        if (realm.clients().isEmpty()) {
            return Optional.empty();
        }
        List<Choice<Optional<TemplatedValue>>> choices = new ArrayList<>();
        choices.add(new Choice<>(Optional.empty(), "The realm", "a role every client can give"));
        realm.clients()
                .forEach(defined -> choices.add(new Choice<>(
                        Optional.of(defined.definition().clientId()),
                        "Client '" + defined.definition().clientId().raw() + "'",
                        "a role of this client")));
        return questions.choose("Role of", choices, Optional.of(Optional.empty()));
    }

    private static TemplatedValue name(
            final RealmDefinition realm,
            final Optional<TemplatedValue> client,
            final RoleAnswers given,
            final Questions questions) {
        if (given.name().isPresent()) {
            TemplatedValue name = given.name().get();
            if (taken(realm, client, name)) {
                throw new RoleExistsException(name.raw());
            }
            return name;
        }
        while (true) {
            TemplatedValue name = questions.askValue("Name", Optional.empty());
            if (!taken(realm, client, name)) {
                return name;
            }
            questions.tell("Role '" + name.raw() + "' already exists");
        }
    }

    private static boolean taken(
            final RealmDefinition realm, final Optional<TemplatedValue> client, final TemplatedValue name) {
        return realm.roles().stream()
                .map(defined -> defined.definition())
                .anyMatch(role -> role.name().raw().equals(name.raw())
                        && role.clientId().map(TemplatedValue::raw).equals(client.map(TemplatedValue::raw)));
    }
}
