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
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits the roles of a group by guided questions (ADR-008): the current roles are proposed in the list of the roles
 * that can be given, so an empty answer keeps them. The name and the path identify the group and are never changed.
 */
public final class EditGroup {

    private static final String NOT_FOUND = "(not found)";

    private final RealmDefinitions definitions;
    private final SaveElement saveElement;
    private final RuleBook rules;

    public EditGroup(final RealmDefinitions definitions, final SaveElement saveElement, final RuleBook rules) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.saveElement = Objects.requireNonNull(saveElement, "saveElement");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * @param path the path of the group, as written
     * @param given the answers already given, which are not asked; its name and parent are ignored. Given realm roles
     *     replace only the realm roles, given client roles only the client roles: when one is given, the roles are not
     *     asked and the other part is kept
     * @return the file written, with what the rules say about the group
     * @throws UnknownRealmFolderException when the folder is not a realm of the shell
     * @throws UnknownGroupException when no group of the folder has this path
     * @throws UnknownAssignableRoleException when a given role is not in the catalogue
     * @throws RuleViolationException when the group fails a required rule: nothing is written
     * @throws MissingAnswerException when an answer is missing and cannot be asked
     */
    public SaveResult edit(
            final String folder, final String path, final GroupAnswers given, final Questions questions) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        Defined<GroupDefinition> existing = realm.groups().stream()
                .filter(defined -> defined.definition().path().equals(path))
                .findFirst()
                .orElseThrow(() -> new UnknownGroupException(path));
        GroupDefinition current = existing.definition();
        List<AssignableRole> catalogue = RoleCatalog.of(realm, this.rules.builtIns());
        List<AssignableRole> roles;
        if (given.realmRoles().isPresent() || given.clientRoles().isPresent()) {
            roles = new ArrayList<>();
            if (given.realmRoles().isPresent()) {
                given.realmRoles().get().forEach(name -> roles.add(CreateGroup.find(catalogue, name, false)));
            } else {
                roles.addAll(currentRealmRoles(current));
            }
            if (given.clientRoles().isPresent()) {
                given.clientRoles().get().forEach(entry -> roles.add(CreateGroup.find(catalogue, entry, true)));
            } else {
                roles.addAll(currentClientRoles(current));
            }
        } else {
            roles = choose(catalogue, current, questions);
        }
        GroupDefinition group = new GroupDefinition(
                current.path(), current.name(), CreateGroup.realmRoles(roles), CreateGroup.clientRoles(roles));
        return this.saveElement.save(folder, group, Optional.of(existing.origin()), true);
    }

    private static List<AssignableRole> choose(
            final List<AssignableRole> catalogue, final GroupDefinition current, final Questions questions) {
        List<Choice<AssignableRole>> choices = new ArrayList<>();
        catalogue.forEach(role -> choices.add(new Choice<>(role, role.label(), role.explanation())));
        List<AssignableRole> proposed = new ArrayList<>();
        List<AssignableRole> held = new ArrayList<>(currentRealmRoles(current));
        held.addAll(currentClientRoles(current));
        for (AssignableRole role : held) {
            Optional<AssignableRole> known = catalogue.stream()
                    .filter(entry ->
                            entry.client().equals(role.client()) && entry.name().equals(role.name()))
                    .findFirst();
            if (known.isPresent()) {
                proposed.add(known.get());
            } else {
                choices.add(new Choice<>(role, role.label(), role.explanation() + " " + NOT_FOUND));
                proposed.add(role);
            }
        }
        return questions.chooseMany("Roles", choices, proposed);
    }

    private static List<AssignableRole> currentRealmRoles(final GroupDefinition group) {
        return group.realmRoles().stream()
                .sorted()
                .map(name -> new AssignableRole(Optional.empty(), name, false))
                .toList();
    }

    private static List<AssignableRole> currentClientRoles(final GroupDefinition group) {
        List<AssignableRole> roles = new ArrayList<>();
        group.clientRoles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().stream()
                        .sorted()
                        .forEach(name -> roles.add(new AssignableRole(Optional.of(entry.getKey()), name, false))));
        return roles;
    }
}
