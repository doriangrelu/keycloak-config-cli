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
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Creates a group, at the top or under another, by guided questions: its roles are chosen in a list (ADR-008). */
public final class CreateGroup {

    private static final String EMPTY = "it cannot be empty";

    private final RealmDefinitions definitions;
    private final SaveElement saveElement;
    private final RuleBook rules;

    public CreateGroup(final RealmDefinitions definitions, final SaveElement saveElement, final RuleBook rules) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.saveElement = Objects.requireNonNull(saveElement, "saveElement");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Like {@link #create(String, GroupAnswers, Questions, DeclarationWarning)}, with the general warning of a first
     * declaration.
     */
    public CreatedGroup create(final String folder, final GroupAnswers given, final Questions questions) {
        return create(folder, given, questions, DeclarationWarning.OFFLINE);
    }

    /**
     * @param given the answers already given, which are not asked
     * @param warning what the first element of its type is confirmed with
     * @return the path the group was written under, with the file written and what the rules say about it
     * @throws UnknownRealmFolderException when the folder is not a realm of the shell
     * @throws UnknownGroupException when the given parent is not a group of the folder
     * @throws InvalidGroupNameException when the given name is empty or contains a slash
     * @throws GroupExistsException when the given name is taken under the parent
     * @throws UnknownAssignableRoleException when a given role is not in the catalogue
     * @throws ParentGroupElsewhereException when the parent is written in another file than the groups are
     * @throws FirstDeclarationException when the group is the first of the realm and this is not confirmed
     * @throws RuleViolationException when the group fails a required rule: nothing is written
     * @throws MissingAnswerException when an answer is missing and cannot be asked
     */
    public CreatedGroup create(
            final String folder,
            final GroupAnswers given,
            final Questions questions,
            final DeclarationWarning warning) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        Optional<String> parent = parent(realm, given, questions);
        parent.ifPresent(path -> checkSameFile(folder, realm, path));
        TemplatedValue name = name(realm, parent, given, questions);
        List<AssignableRole> roles = roles(RoleCatalog.of(realm, this.rules.builtIns()), given, questions);
        GroupDefinition group = new GroupDefinition(path(parent, name), name, realmRoles(roles), clientRoles(roles));
        return new CreatedGroup(
                group.path(), this.saveElement.saveNew(folder, realm, group, given.declareType(), questions, warning));
    }

    private static Optional<String> parent(
            final RealmDefinition realm, final GroupAnswers given, final Questions questions) {
        List<String> paths = realm.groups().stream()
                .map(defined -> defined.definition().path())
                .sorted()
                .toList();
        if (given.parent().isPresent()) {
            String parent = given.parent().get();
            if (!paths.contains(parent)) {
                throw new UnknownGroupException(parent);
            }
            return Optional.of(parent);
        }
        if (paths.isEmpty()) {
            return Optional.empty();
        }
        List<Choice<Optional<String>>> choices = new ArrayList<>();
        choices.add(new Choice<>(Optional.empty(), "None", "a top-level group"));
        paths.forEach(path -> choices.add(new Choice<>(Optional.of(path), path, "a subgroup of this group")));
        return questions.choose("Parent group", choices, Optional.of(Optional.empty()));
    }

    private static TemplatedValue name(
            final RealmDefinition realm,
            final Optional<String> parent,
            final GroupAnswers given,
            final Questions questions) {
        if (given.name().isPresent()) {
            TemplatedValue name = given.name().get();
            invalid(name).ifPresent(reason -> {
                throw new InvalidGroupNameException(name.raw(), reason);
            });
            if (taken(realm, path(parent, name))) {
                throw new GroupExistsException(path(parent, name));
            }
            return name;
        }
        while (true) {
            TemplatedValue name = questions.askValue("Name", Optional.empty());
            Optional<String> invalid = invalid(name);
            if (invalid.isPresent()) {
                questions.tell(
                        invalid.get().equals(EMPTY)
                                ? "A group name cannot be empty"
                                : "A group name cannot contain '/': choose the parent with the parent question");
                continue;
            }
            String path = path(parent, name);
            if (!taken(realm, path)) {
                return name;
            }
            questions.tell("Group '" + path + "' already exists");
        }
    }

    private static Optional<String> invalid(final TemplatedValue name) {
        if (name.raw().isBlank()) {
            return Optional.of(EMPTY);
        }
        return name.raw().contains("/") ? Optional.of("it cannot contain '/'") : Optional.empty();
    }

    private static String path(final Optional<String> parent, final TemplatedValue name) {
        return parent.orElse("") + "/" + name.raw();
    }

    private static boolean taken(final RealmDefinition realm, final String path) {
        return realm.groups().stream()
                .anyMatch(defined -> defined.definition().path().equals(path));
    }

    private static List<AssignableRole> roles(
            final List<AssignableRole> catalogue, final GroupAnswers given, final Questions questions) {
        if (given.realmRoles().isPresent() || given.clientRoles().isPresent()) {
            List<AssignableRole> roles = new ArrayList<>();
            given.realmRoles().orElse(List.of()).forEach(name -> roles.add(find(catalogue, name, false)));
            given.clientRoles().orElse(List.of()).forEach(entry -> roles.add(find(catalogue, entry, true)));
            return roles;
        }
        List<Choice<AssignableRole>> choices = catalogue.stream()
                .map(role -> new Choice<>(role, role.label(), role.explanation()))
                .toList();
        return questions.chooseMany("Roles", choices, List.of());
    }

    static AssignableRole find(final List<AssignableRole> catalogue, final String given, final boolean client) {
        return catalogue.stream()
                .filter(role ->
                        role.client().isPresent() == client && role.label().equals(given))
                .findFirst()
                .orElseThrow(() -> new UnknownAssignableRoleException(given));
    }

    static Set<String> realmRoles(final List<AssignableRole> roles) {
        Set<String> names = new LinkedHashSet<>();
        roles.stream().filter(role -> role.client().isEmpty()).forEach(role -> names.add(role.name()));
        return names;
    }

    static Map<String, Set<String>> clientRoles(final List<AssignableRole> roles) {
        Map<String, Set<String>> byClient = new LinkedHashMap<>();
        roles.stream()
                .filter(role -> role.client().isPresent())
                .forEach(role -> byClient.computeIfAbsent(role.client().get(), id -> new LinkedHashSet<>())
                        .add(role.name()));
        return byClient;
    }

    /** A subgroup is written in the file of its parent, which must be the file the groups are written to. */
    private void checkSameFile(final String folder, final RealmDefinition realm, final String parent) {
        Optional<String> groupsFile = this.definitions.fileOf(folder, ElementType.GROUP);
        Optional<String> parentFile = realm.groups().stream()
                .filter(defined -> defined.definition().path().equals(parent))
                .map(defined -> defined.origin().file())
                .findFirst();
        if (groupsFile.isPresent()
                && parentFile.isPresent()
                && !groupsFile.get().equals(parentFile.get())) {
            throw new ParentGroupElsewhereException(parent, parentFile.get(), groupsFile.get());
        }
    }
}
