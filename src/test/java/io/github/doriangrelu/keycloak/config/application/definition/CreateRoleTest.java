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

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CreateRoleTest {

    private static final String REALM_OWNER = "";
    private static final String FIRST_CLIENT = "2";

    private static TemplatedValue value(final String raw) {
        return TemplatedValue.of(raw);
    }

    private static Defined<ClientDefinition> client(final String clientId) {
        return new Defined<>(
                new ClientDefinition(
                        value(clientId),
                        Optional.empty(),
                        Optional.empty(),
                        ApplicationType.API,
                        List.of(),
                        List.of(),
                        Map.of(),
                        Optional.empty()),
                new Origin("20-clients.yaml", 3));
    }

    private static Defined<RoleDefinition> role(final String name, final String clientId) {
        return new Defined<>(
                new RoleDefinition(
                        value(name), Optional.ofNullable(clientId).map(TemplatedValue::of), Optional.empty()),
                new Origin("30-roles.yaml", 3));
    }

    /** A realm whose files declare exactly the roles given: the realm roles, and the roles of these clients. */
    @SafeVarargs
    private static FakeRealmDefinitions declared(final List<String> clientIds, final Defined<RoleDefinition>... roles) {
        Set<String> declaredClients = Arrays.stream(roles)
                .flatMap(defined -> defined.definition().clientId().stream())
                .map(TemplatedValue::raw)
                .collect(Collectors.toSet());
        boolean realmRoles = Arrays.stream(roles)
                .anyMatch(defined -> defined.definition().clientId().isEmpty());
        return new FakeRealmDefinitions(new RealmDefinition(
                "demo",
                Optional.empty(),
                clientIds.stream().map(CreateRoleTest::client).toList(),
                List.of(roles),
                List.of(),
                Set.of(ElementType.CLIENT, ElementType.ROLE),
                realmRoles,
                declaredClients,
                List.of()));
    }

    private static CreateRole useCase(final FakeRealmDefinitions definitions) {
        return new CreateRole(definitions, new SaveElement(definitions, Rules.none()), Rules.none());
    }

    private static CreateRole useCaseWithBuiltIns(final FakeRealmDefinitions definitions) {
        RuleBook rules = Rules.withBuiltInClients("realm-management");
        return new CreateRole(definitions, new SaveElement(definitions, rules), rules);
    }

    private static RoleDefinition written(final FakeRealmDefinitions definitions) {
        assertThat(definitions.saved).hasSize(1);
        return (RoleDefinition) definitions.saved.getFirst();
    }

    private static RoleAnswers given(final String client, final String name) {
        return new RoleAnswers(
                Optional.ofNullable(client).map(TemplatedValue::of),
                Optional.ofNullable(name).map(TemplatedValue::of),
                Optional.empty(),
                false);
    }

    @Test
    void aRealmRoleIsProposedFirst() {
        FakeRealmDefinitions definitions = declared(List.of("shop"), role("admin", null));
        ScriptedQuestions questions = new ScriptedQuestions(REALM_OWNER, "editor", "Edits things");

        useCase(definitions).create("demo", RoleAnswers.none(), questions);

        RoleDefinition role = written(definitions);
        assertThat(role.name()).isEqualTo(value("editor"));
        assertThat(role.clientId()).isEmpty();
        assertThat(role.description()).contains(value("Edits things"));
        assertThat(questions.asked()).containsExactly("Role of", "Name", "Description");
    }

    @Test
    void aClientRoleIsChosenFromTheDefinedClients() {
        FakeRealmDefinitions definitions = declared(List.of("shop", "back"), role("admin", "back"));
        ScriptedQuestions questions = new ScriptedQuestions("3", "reader", "");

        useCase(definitions).create("demo", RoleAnswers.none(), questions);

        RoleDefinition role = written(definitions);
        assertThat(role.clientId()).contains(value("back"));
        assertThat(role.description()).isEmpty();
    }

    @Test
    void theOwnerIsNotAskedWithoutClient() {
        FakeRealmDefinitions definitions = declared(List.of(), role("admin", null));
        ScriptedQuestions questions = new ScriptedQuestions("editor", "");

        useCase(definitions).create("demo", RoleAnswers.none(), questions);

        assertThat(written(definitions).clientId()).isEmpty();
        assertThat(questions.asked()).doesNotContain("Role of").containsExactly("Name", "Description");
    }

    @Test
    void aTakenNameIsAskedAgain() {
        FakeRealmDefinitions definitions = declared(List.of("shop"), role("admin", null));
        ScriptedQuestions questions = new ScriptedQuestions(REALM_OWNER, "admin", "editor", "");

        useCase(definitions).create("demo", RoleAnswers.none(), questions);

        assertThat(written(definitions).name()).isEqualTo(value("editor"));
        assertThat(questions.told()).containsExactly("Role 'admin' already exists");
    }

    @Test
    void theSameNameForAnotherClientIsAccepted() {
        FakeRealmDefinitions definitions =
                declared(List.of("shop", "back"), role("admin", null), role("admin", "back"), role("x", "shop"));
        ScriptedQuestions questions = new ScriptedQuestions(FIRST_CLIENT, "admin", "");

        useCase(definitions).create("demo", RoleAnswers.none(), questions);

        assertThat(written(definitions).clientId()).contains(value("shop"));
        assertThat(questions.told()).isEmpty();
    }

    @Test
    void aTakenGivenNameFails() {
        FakeRealmDefinitions definitions = declared(List.of("shop"), role("admin", "shop"));

        assertThatThrownBy(() -> useCase(definitions).create("demo", given("shop", "admin"), new ScriptedQuestions()))
                .isInstanceOf(RoleExistsException.class)
                .hasMessage("Role 'admin' already exists");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void anUnknownGivenClientFails() {
        FakeRealmDefinitions definitions = declared(List.of("shop"), role("admin", null));

        assertThatThrownBy(() -> useCase(definitions).create("demo", given("ghost", "admin"), new ScriptedQuestions()))
                .isInstanceOf(UnknownClientException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void aClientCreatedByKeycloakIsNotOffered() {
        FakeRealmDefinitions definitions = declared(List.of("realm-management", "shop"), role("admin", null));

        useCaseWithBuiltIns(definitions)
                .create("demo", RoleAnswers.none(), new ScriptedQuestions("2", "editor", "", "y"));

        assertThat(written(definitions).clientId()).contains(value("shop"));
        FakeRealmDefinitions other = declared(List.of("realm-management", "shop"), role("admin", null));
        assertThatThrownBy(() -> useCaseWithBuiltIns(other)
                        .create("demo", RoleAnswers.none(), new ScriptedQuestions("3", "editor", "")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(other.saved).isEmpty();
    }

    @Test
    void aGivenClientCreatedByKeycloakFails() {
        FakeRealmDefinitions definitions = declared(List.of("realm-management", "shop"), role("admin", null));

        assertThatThrownBy(() -> useCaseWithBuiltIns(definitions)
                        .create("demo", given("realm-management", "x"), new ScriptedQuestions()))
                .isInstanceOf(BuiltInClientException.class)
                .hasMessage("Client 'realm-management' is created by Keycloak: its roles are not edited by the shell");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void theFirstRoleOfAClientNeedsAConfirmation() {
        FakeRealmDefinitions definitions = declared(List.of("shop"), role("admin", null));
        ScriptedQuestions questions = new ScriptedQuestions(FIRST_CLIENT, "reader", "", "n");

        assertThatThrownBy(() -> useCase(definitions).create("demo", RoleAnswers.none(), questions))
                .isInstanceOf(FirstDeclarationException.class);
        assertThat(definitions.saved).isEmpty();
        assertThat(questions.asked().getLast()).contains("roles of client 'shop'");
    }

    @Test
    void theFirstRoleOfAClientIsWrittenOnceConfirmed() {
        FakeRealmDefinitions definitions = declared(List.of("shop"), role("admin", null));
        ScriptedQuestions questions = new ScriptedQuestions(FIRST_CLIENT, "reader", "", "y");

        useCase(definitions).create("demo", RoleAnswers.none(), questions);

        assertThat(written(definitions).clientId()).contains(value("shop"));
    }
}
