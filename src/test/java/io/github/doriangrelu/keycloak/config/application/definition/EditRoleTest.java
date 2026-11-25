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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EditRoleTest {

    private static TemplatedValue value(final String raw) {
        return TemplatedValue.of(raw);
    }

    private static Defined<RoleDefinition> role(final String name, final String clientId, final String description) {
        return new Defined<>(
                new RoleDefinition(
                        value(name),
                        Optional.ofNullable(clientId).map(TemplatedValue::of),
                        Optional.ofNullable(description).map(TemplatedValue::of)),
                new Origin(clientId == null ? "30-roles.yaml" : "31-client-roles.yaml", 3));
    }

    private static FakeRealmDefinitions declared(final Defined<RoleDefinition>... roles) {
        Defined<ClientDefinition> shop = new Defined<>(
                new ClientDefinition(
                        value("shop"),
                        Optional.empty(),
                        Optional.empty(),
                        ApplicationType.API,
                        List.of(),
                        List.of(),
                        Map.of(),
                        Optional.empty()),
                new Origin("20-clients.yaml", 3));
        return new FakeRealmDefinitions(new RealmDefinition(
                "demo",
                Optional.empty(),
                List.of(shop),
                List.of(roles),
                List.of(),
                Set.of(ElementType.CLIENT, ElementType.ROLE),
                true,
                Set.of("shop"),
                List.of()));
    }

    private static EditRole useCase(final FakeRealmDefinitions definitions) {
        return new EditRole(definitions, new SaveElement(definitions, Rules.none()));
    }

    private static RoleDefinition written(final FakeRealmDefinitions definitions) {
        assertThat(definitions.saved).hasSize(1);
        return (RoleDefinition) definitions.saved.getFirst();
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyTheDescriptionIsAsked() {
        FakeRealmDefinitions definitions = declared(role("admin", null, "Administers"));
        ScriptedQuestions questions = new ScriptedQuestions("Rules everything");

        useCase(definitions).edit("demo", "admin", Optional.empty(), RoleAnswers.none(), questions);

        assertThat(questions.asked()).containsExactly("Description");
        RoleDefinition role = written(definitions);
        assertThat(role.name()).isEqualTo(value("admin"));
        assertThat(role.description()).contains(value("Rules everything"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyAnswerKeepsTheDescription() {
        FakeRealmDefinitions definitions = declared(role("admin", null, "Administers"));

        useCase(definitions).edit("demo", "admin", Optional.empty(), RoleAnswers.none(), new ScriptedQuestions(""));

        assertThat(written(definitions).description()).contains(value("Administers"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDashClearsTheDescription() {
        FakeRealmDefinitions definitions = declared(role("admin", null, "Administers"));

        useCase(definitions).edit("demo", "admin", Optional.empty(), RoleAnswers.none(), new ScriptedQuestions("-"));

        assertThat(written(definitions).description()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aClientRoleIsFoundByItsClient() {
        FakeRealmDefinitions definitions =
                declared(role("admin", null, "Realm one"), role("admin", "shop", "Client one"));
        ScriptedQuestions questions = new ScriptedQuestions("");

        useCase(definitions).edit("demo", "admin", Optional.of("shop"), RoleAnswers.none(), questions);

        RoleDefinition role = written(definitions);
        assertThat(role.clientId()).contains(value("shop"));
        assertThat(role.description()).contains(value("Client one"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aGivenDescriptionIsNotAsked() {
        FakeRealmDefinitions definitions = declared(role("admin", null, "Administers"));
        ScriptedQuestions questions = new ScriptedQuestions();
        RoleAnswers given = new RoleAnswers(Optional.empty(), Optional.empty(), Optional.of(value("Given")), false);

        useCase(definitions).edit("demo", "admin", Optional.empty(), given, questions);

        assertThat(questions.asked()).isEmpty();
        assertThat(written(definitions).description()).contains(value("Given"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUnknownRoleFails() {
        FakeRealmDefinitions definitions = declared(role("admin", "shop", null));

        assertThatThrownBy(() -> useCase(definitions)
                        .edit("demo", "admin", Optional.empty(), RoleAnswers.none(), new ScriptedQuestions()))
                .isInstanceOf(UnknownRoleException.class)
                .hasMessage("Role 'admin' does not exist in the realm");
        assertThatThrownBy(() -> useCase(definitions)
                        .edit("demo", "ghost", Optional.of("shop"), RoleAnswers.none(), new ScriptedQuestions()))
                .isInstanceOf(UnknownRoleException.class)
                .hasMessage("Role 'ghost' does not exist in the realm of client 'shop'");
        assertThat(definitions.saved).isEmpty();
    }
}
