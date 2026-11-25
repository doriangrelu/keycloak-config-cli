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

class CreateGroupTest {

    private static final String TOP_LEVEL = "";

    private static Defined<GroupDefinition> group(final String path, final String file) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new Defined<>(
                new GroupDefinition(path, TemplatedValue.of(name), Set.of(), Map.of()), new Origin(file, 3));
    }

    private static Defined<RoleDefinition> role(final String name, final String clientId) {
        return new Defined<>(
                new RoleDefinition(
                        TemplatedValue.of(name),
                        Optional.ofNullable(clientId).map(TemplatedValue::of),
                        Optional.empty()),
                new Origin("30-roles.yaml", 3));
    }

    @SafeVarargs
    private static FakeRealmDefinitions declared(
            final List<Defined<RoleDefinition>> roles, final Defined<GroupDefinition>... groups) {
        return new FakeRealmDefinitions(new RealmDefinition(
                "demo",
                Optional.empty(),
                List.of(),
                roles,
                List.of(groups),
                groups.length == 0 ? Set.of(ElementType.ROLE) : Set.of(ElementType.ROLE, ElementType.GROUP),
                List.of()));
    }

    private static CreateGroup useCase(final FakeRealmDefinitions definitions) {
        RuleBook rules = withBuiltIns();
        return new CreateGroup(definitions, new SaveElement(definitions, rules), rules);
    }

    private static RuleBook withBuiltIns() {
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
        return new RuleBook(files, List.of(), builtIns);
    }

    private static GroupDefinition written(final FakeRealmDefinitions definitions) {
        assertThat(definitions.saved).hasSize(1);
        return (GroupDefinition) definitions.saved.getFirst();
    }

    private static GroupAnswers given(final String name, final String parent) {
        return new GroupAnswers(
                Optional.ofNullable(name).map(TemplatedValue::of),
                Optional.ofNullable(parent),
                Optional.of(List.of()),
                Optional.empty(),
                false);
    }

    @Test
    void aTopLevelGroupIsProposed() {
        FakeRealmDefinitions definitions = declared(List.of(), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions(TOP_LEVEL, "ops", TOP_LEVEL);

        useCase(definitions).create("demo", GroupAnswers.none(), questions);

        GroupDefinition group = written(definitions);
        assertThat(group.path()).isEqualTo("/ops");
        assertThat(group.realmRoles()).isEmpty();
        assertThat(questions.asked()).containsExactly("Parent group", "Name", "Roles");
    }

    @Test
    void theParentIsNotAskedWithoutGroup() {
        FakeRealmDefinitions definitions = declared(List.of());
        ScriptedQuestions questions = new ScriptedQuestions("ops", TOP_LEVEL);
        GroupAnswers confirmed =
                new GroupAnswers(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);

        useCase(definitions).create("demo", confirmed, questions);

        assertThat(written(definitions).path()).isEqualTo("/ops");
        assertThat(questions.asked()).containsExactly("Name", "Roles");
    }

    @Test
    void aSubgroupIsWrittenUnderItsParent() {
        FakeRealmDefinitions definitions =
                declared(List.of(), group("/team", "40-groups.yaml"), group("/ops", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions("3", "devs", TOP_LEVEL);

        CreatedGroup created = useCase(definitions).create("demo", GroupAnswers.none(), questions);

        assertThat(created.path()).isEqualTo("/team/devs");
        GroupDefinition group = written(definitions);
        assertThat(group.path()).isEqualTo("/team/devs");
        assertThat(group.name()).isEqualTo(TemplatedValue.of("devs"));
    }

    @Test
    void aTakenPathIsAskedAgain() {
        FakeRealmDefinitions definitions = declared(List.of(), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions(TOP_LEVEL, "team", "ops", TOP_LEVEL);

        useCase(definitions).create("demo", GroupAnswers.none(), questions);

        assertThat(written(definitions).path()).isEqualTo("/ops");
        assertThat(questions.told()).containsExactly("Group '/team' already exists");
    }

    @Test
    void aGivenTakenPathFails() {
        FakeRealmDefinitions definitions = declared(List.of(), group("/team", "40-groups.yaml"));
        GroupAnswers answers = given("team", null);
        ScriptedQuestions questions = new ScriptedQuestions(TOP_LEVEL);

        assertThatThrownBy(() -> useCase(definitions).create("demo", answers, questions))
                .isInstanceOf(GroupExistsException.class)
                .hasMessage("Group '/team' already exists");
    }

    @Test
    void theSameNameUnderAnotherParentIsAccepted() {
        FakeRealmDefinitions definitions =
                declared(List.of(), group("/team", "40-groups.yaml"), group("/team/devs", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions("1", "devs", TOP_LEVEL);

        useCase(definitions).create("demo", GroupAnswers.none(), questions);

        assertThat(written(definitions).path()).isEqualTo("/devs");
    }

    @Test
    void rolesAreChosenFromTheCatalogue() {
        FakeRealmDefinitions definitions =
                declared(List.of(role("admin", null), role("writer", "shop")), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions(TOP_LEVEL, "ops", "1,2,3,4");

        useCase(definitions).create("demo", GroupAnswers.none(), questions);

        GroupDefinition group = written(definitions);
        assertThat(group.realmRoles()).containsExactlyInAnyOrder("admin", "offline_access");
        assertThat(group.clientRoles()).containsOnlyKeys("shop", "account");
        assertThat(group.clientRoles().get("shop")).containsExactly("writer");
    }

    @Test
    void givenRolesAreNotAskedAndMustBeInTheCatalogue() {
        FakeRealmDefinitions definitions =
                declared(List.of(role("admin", null), role("writer", "shop")), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions();
        GroupAnswers answers = new GroupAnswers(
                Optional.of(TemplatedValue.of("ops")),
                Optional.of("/team"),
                Optional.of(List.of("admin")),
                Optional.of(List.of("shop/writer", "account/view")),
                false);

        useCase(definitions).create("demo", answers, questions);

        GroupDefinition group = written(definitions);
        assertThat(group.path()).isEqualTo("/team/ops");
        assertThat(group.realmRoles()).containsExactly("admin");
        assertThat(group.clientRoles()).containsOnlyKeys("shop", "account");
        assertThat(questions.asked()).isEmpty();
    }

    @Test
    void anUnknownGivenRoleFails() {
        FakeRealmDefinitions definitions = declared(List.of(role("admin", null)), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions();
        GroupAnswers unknownClientRole = new GroupAnswers(
                Optional.of(TemplatedValue.of("ops")),
                Optional.of("/team"),
                Optional.of(List.of("admin")),
                Optional.of(List.of("shop/ghost")),
                false);
        GroupAnswers clientRoleAsRealmRole = new GroupAnswers(
                Optional.of(TemplatedValue.of("ops")),
                Optional.of("/team"),
                Optional.of(List.of("shop/admin")),
                Optional.empty(),
                false);

        assertThatThrownBy(() -> useCase(definitions).create("demo", unknownClientRole, questions))
                .isInstanceOf(UnknownAssignableRoleException.class)
                .hasMessageContaining("shop/ghost");
        assertThatThrownBy(() -> useCase(definitions).create("demo", clientRoleAsRealmRole, questions))
                .isInstanceOf(UnknownAssignableRoleException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void anUnknownGivenParentFails() {
        FakeRealmDefinitions definitions = declared(List.of(), group("/team", "40-groups.yaml"));
        GroupAnswers answers = given("ops", "/ghost");
        ScriptedQuestions questions = new ScriptedQuestions();

        assertThatThrownBy(() -> useCase(definitions).create("demo", answers, questions))
                .isInstanceOf(UnknownGroupException.class)
                .hasMessage("Group '/ghost' does not exist");
    }

    @Test
    void theFirstGroupNeedsAConfirmation() {
        FakeRealmDefinitions definitions = declared(List.of());
        GroupAnswers answers = given("ops", null);

        assertThatThrownBy(() -> useCase(definitions).create("demo", answers, new ScriptedQuestions("")))
                .isInstanceOf(FirstDeclarationException.class);
        assertThat(definitions.saved).isEmpty();

        useCase(definitions).create("demo", answers, new ScriptedQuestions("y"));
        assertThat(written(definitions).path()).isEqualTo("/ops");
    }

    @Test
    void aParentWrittenInAnotherFileIsRefused() {
        FakeRealmDefinitions definitions =
                declared(List.of(), group("/team", "40-groups.yaml"), group("/ops", "41-more-groups.yaml"));
        definitions.files.put(ElementType.GROUP, "40-groups.yaml");
        GroupAnswers answers = given("devs", "/ops");
        ScriptedQuestions questions = new ScriptedQuestions();

        assertThatThrownBy(() -> useCase(definitions).create("demo", answers, questions))
                .isInstanceOf(ParentGroupElsewhereException.class)
                .hasMessageContaining("41-more-groups.yaml");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void aNameWithASlashIsAskedAgain() {
        FakeRealmDefinitions definitions = declared(List.of(), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions(TOP_LEVEL, "a/b", "ops", TOP_LEVEL);

        useCase(definitions).create("demo", GroupAnswers.none(), questions);

        assertThat(written(definitions).path()).isEqualTo("/ops");
        assertThat(questions.told())
                .containsExactly("A group name cannot contain '/': choose the parent with the parent question");
    }

    @Test
    void aGivenNameWithASlashOrEmptyFails() {
        FakeRealmDefinitions definitions = declared(List.of(), group("/team", "40-groups.yaml"));
        ScriptedQuestions questions = new ScriptedQuestions(TOP_LEVEL, TOP_LEVEL);

        assertThatThrownBy(() -> useCase(definitions).create("demo", given("a/b", null), questions))
                .isInstanceOf(InvalidGroupNameException.class)
                .hasMessageContaining("a/b");
        assertThatThrownBy(() -> useCase(definitions).create("demo", given(" ", null), questions))
                .isInstanceOf(InvalidGroupNameException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void theParentFileIsCheckedBeforeTheOtherQuestions() {
        FakeRealmDefinitions definitions =
                declared(List.of(), group("/team", "40-groups.yaml"), group("/ops", "41-more-groups.yaml"));
        definitions.files.put(ElementType.GROUP, "40-groups.yaml");
        ScriptedQuestions questions = new ScriptedQuestions("2");

        assertThatThrownBy(() -> useCase(definitions).create("demo", GroupAnswers.none(), questions))
                .isInstanceOf(ParentGroupElsewhereException.class);
        assertThat(questions.asked()).containsExactly("Parent group");
    }
}
