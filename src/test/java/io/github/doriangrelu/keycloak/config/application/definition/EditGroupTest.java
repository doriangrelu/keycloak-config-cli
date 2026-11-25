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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakBuiltIns;
import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EditGroupTest {

    private static final String KEEP = "";

    private static Defined<GroupDefinition> group(
            final String path, final Set<String> realmRoles, final Map<String, Set<String>> clientRoles) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new Defined<>(
                new GroupDefinition(path, TemplatedValue.of(name), realmRoles, clientRoles),
                new Origin("40-groups.yaml", 3));
    }

    private static Defined<RoleDefinition> role(final String name, final String clientId) {
        return new Defined<>(
                new RoleDefinition(
                        TemplatedValue.of(name),
                        Optional.ofNullable(clientId).map(TemplatedValue::of),
                        Optional.empty()),
                new Origin("30-roles.yaml", 3));
    }

    /** The catalogue is: admin, shop/writer, offline_access, account/view. */
    @SafeVarargs
    private static FakeRealmDefinitions declared(final Defined<GroupDefinition>... groups) {
        return new FakeRealmDefinitions(new RealmDefinition(
                "demo",
                Optional.empty(),
                List.of(),
                List.of(role("admin", null), role("writer", "shop")),
                List.of(groups),
                Set.of(ElementType.ROLE, ElementType.GROUP),
                List.of()));
    }

    private static EditGroup useCase(final FakeRealmDefinitions definitions) {
        RuleFiles files = new RuleFiles() {
            @Override
            public List<Rule> base() {
                return List.of();
            }

            @Override
            public OrganisationRules organisation() {
                return OrganisationRules.none();
            }
        };
        KeycloakBuiltIns builtIns =
                () -> new BuiltIns(Set.of(), Set.of("offline_access"), Map.of("account", Set.of("view")));
        RuleBook rules = new RuleBook(files, List.of(), builtIns);
        return new EditGroup(definitions, new SaveElement(definitions, rules), rules);
    }

    private static GroupDefinition written(final FakeRealmDefinitions definitions) {
        assertThat(definitions.saved).hasSize(1);
        return (GroupDefinition) definitions.saved.getFirst();
    }

    private static Defined<GroupDefinition> team() {
        return group("/team", Set.of("admin"), Map.of("shop", Set.of("writer")));
    }

    private static GroupAnswers roles(final List<String> realmRoles, final List<String> clientRoles) {
        return new GroupAnswers(
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(realmRoles),
                Optional.ofNullable(clientRoles),
                false);
    }

    @Test
    void onlyTheRolesAreAsked() {
        FakeRealmDefinitions definitions = declared(team());
        ScriptedQuestions questions = new ScriptedQuestions("3,4");

        useCase(definitions).edit("demo", "/team", GroupAnswers.none(), questions);

        GroupDefinition group = written(definitions);
        assertThat(group.path()).isEqualTo("/team");
        assertThat(group.name()).isEqualTo(TemplatedValue.of("team"));
        assertThat(group.realmRoles()).containsExactly("offline_access");
        assertThat(group.clientRoles()).containsOnlyKeys("account");
        assertThat(questions.asked()).containsExactly("Roles");
    }

    @Test
    void enterKeepsTheCurrentRoles() {
        FakeRealmDefinitions definitions = declared(team());

        useCase(definitions).edit("demo", "/team", GroupAnswers.none(), new ScriptedQuestions(KEEP));

        GroupDefinition group = written(definitions);
        assertThat(group.realmRoles()).containsExactly("admin");
        assertThat(group.clientRoles()).containsOnlyKeys("shop");
        assertThat(group.clientRoles().get("shop")).containsExactly("writer");
    }

    @Test
    void aDashRemovesAllRoles() {
        FakeRealmDefinitions definitions = declared(team());

        useCase(definitions).edit("demo", "/team", GroupAnswers.none(), new ScriptedQuestions("-"));

        GroupDefinition group = written(definitions);
        assertThat(group.realmRoles()).isEmpty();
        assertThat(group.clientRoles()).isEmpty();
    }

    @Test
    void givenRealmRolesReplaceOnlyTheRealmRoles() {
        FakeRealmDefinitions definitions = declared(team());
        ScriptedQuestions questions = new ScriptedQuestions();

        useCase(definitions).edit("demo", "/team", roles(List.of("offline_access"), null), questions);

        GroupDefinition group = written(definitions);
        assertThat(group.realmRoles()).containsExactly("offline_access");
        assertThat(group.clientRoles().get("shop")).containsExactly("writer");
        assertThat(questions.asked()).isEmpty();
    }

    @Test
    void givenClientRolesReplaceOnlyTheClientRoles() {
        FakeRealmDefinitions definitions = declared(team());
        ScriptedQuestions questions = new ScriptedQuestions();

        useCase(definitions).edit("demo", "/team", roles(null, List.of("account/view")), questions);

        GroupDefinition group = written(definitions);
        assertThat(group.realmRoles()).containsExactly("admin");
        assertThat(group.clientRoles()).containsOnlyKeys("account");
        assertThat(questions.asked()).isEmpty();
    }

    @Test
    void anUnknownGivenRoleFails() {
        FakeRealmDefinitions definitions = declared(team());
        GroupAnswers answers = roles(List.of("ghost"), null);

        assertThatThrownBy(() -> useCase(definitions).edit("demo", "/team", answers, new ScriptedQuestions()))
                .isInstanceOf(UnknownAssignableRoleException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void aCurrentRoleMissingFromTheCatalogueIsKept() {
        FakeRealmDefinitions definitions = declared(group("/team", Set.of("admin", "ghost"), Map.of()));

        useCase(definitions).edit("demo", "/team", GroupAnswers.none(), new ScriptedQuestions(KEEP));

        assertThat(written(definitions).realmRoles()).containsExactlyInAnyOrder("admin", "ghost");
    }

    @Test
    void aMissingCurrentRoleIsListedLastAndCanBeDropped() {
        FakeRealmDefinitions definitions = declared(group("/team", Set.of("admin", "ghost"), Map.of()));

        useCase(definitions).edit("demo", "/team", GroupAnswers.none(), new ScriptedQuestions("1"));

        assertThat(written(definitions).realmRoles()).containsExactly("admin");
    }

    @Test
    void anUnknownGroupFails() {
        FakeRealmDefinitions definitions = declared(team());
        ScriptedQuestions questions = new ScriptedQuestions();

        assertThatThrownBy(() -> useCase(definitions).edit("demo", "/ghost", GroupAnswers.none(), questions))
                .isInstanceOf(UnknownGroupException.class)
                .hasMessage("Group '/ghost' does not exist");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void anUnknownFolderFails() {
        FakeRealmDefinitions definitions = declared(team());
        ScriptedQuestions questions = new ScriptedQuestions();

        assertThatThrownBy(() -> useCase(definitions).edit("other", "/team", GroupAnswers.none(), questions))
                .isInstanceOf(UnknownRealmFolderException.class);
    }
}
