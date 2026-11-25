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

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateClientTest {

    private static final String SPA = "1";
    private static final String WEB = "2";
    private static final String SERVICE = "3";
    private static final String API = "4";
    private static final String A_VARIABLE = "1";
    private static final String NO_SECRET = "2";
    private static final String NO_DEFAULT_MESSAGE =
            "A secret variable has no default value: a default would be a secret written in the file";

    private static TemplatedValue value(final String raw) {
        return TemplatedValue.of(raw);
    }

    private static ClientDefinition existing(final String clientId) {
        return new ClientDefinition(
                value(clientId),
                Optional.empty(),
                Optional.empty(),
                ApplicationType.API,
                List.of(),
                List.of(),
                Map.of(),
                Optional.empty());
    }

    private static FakeRealmDefinitions realmWith(final String... clientIds) {
        List<Defined<ClientDefinition>> clients = Arrays.stream(clientIds)
                .map(id -> new Defined<>(existing(id), new Origin("20-clients.yaml", 3)))
                .toList();
        Set<ElementType> declared = clients.isEmpty() ? Set.of() : Set.of(ElementType.CLIENT);
        return new FakeRealmDefinitions(
                new RealmDefinition("demo", Optional.empty(), clients, List.of(), List.of(), declared, List.of()));
    }

    private static CreateClient useCase(final FakeRealmDefinitions definitions) {
        return new CreateClient(definitions, new SaveElement(definitions, Rules.none()));
    }

    private static ClientDefinition written(final FakeRealmDefinitions definitions) {
        assertThat(definitions.saved).hasSize(1);
        return (ClientDefinition) definitions.saved.getFirst();
    }

    @Test
    void aSpaIsAskedItsUrlAndGetsProposedUris() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions = new ScriptedQuestions("frontend", "", "", SPA, "{{appUrl}}", "", "");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        ClientDefinition client = written(definitions);
        assertThat(client.clientId()).isEqualTo(value("frontend"));
        assertThat(client.applicationType()).isEqualTo(ApplicationType.SPA);
        assertThat(client.name()).isEmpty();
        assertThat(client.description()).isEmpty();
        assertThat(client.redirectUris()).containsExactly(value("{{appUrl}}/*"));
        assertThat(client.webOrigins()).containsExactly(value("{{appUrl}}"));
        assertThat(client.secret()).isEmpty();
        assertThat(client.attributes()).isEmpty();
        assertThat(questions.asked())
                .containsExactly(
                        "Client id",
                        "Name",
                        "Description",
                        "Application type",
                        "Application URL",
                        "Redirect URIs",
                        "Web origins");
    }

    @Test
    void theNameAndTheDescriptionAreKeptWhenAnswered() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions = new ScriptedQuestions("frontend", "Front", "The front", SPA, "https://a", "", "");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(written(definitions).name()).contains(value("Front"));
        assertThat(written(definitions).description()).contains(value("The front"));
    }

    @Test
    void aWebApplicationIsAskedItsSecret() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions =
                new ScriptedQuestions("shop", "", "", WEB, "https://shop", "", "", A_VARIABLE, "");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        ClientDefinition client = written(definitions);
        assertThat(client.applicationType()).isEqualTo(ApplicationType.WEB_APPLICATION);
        assertThat(client.redirectUris()).containsExactly(value("https://shop/*"));
        assertThat(client.webOrigins()).isEmpty();
        assertThat(client.secret()).contains(value("{{shopSecret}}"));
        assertThat(questions.asked()).endsWith("Web origins", "Client secret", "Secret variable");
    }

    @Test
    void aServiceIsNotAskedAnUrl() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions = new ScriptedQuestions("batch", "", "", SERVICE, A_VARIABLE, "{{jobSecret}}");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        ClientDefinition client = written(definitions);
        assertThat(client.applicationType()).isEqualTo(ApplicationType.SERVICE);
        assertThat(client.redirectUris()).isEmpty();
        assertThat(client.webOrigins()).isEmpty();
        assertThat(client.secret()).contains(value("{{jobSecret}}"));
        assertThat(questions.asked()).doesNotContain("Application URL", "Redirect URIs", "Web origins");
    }

    @Test
    void anApiIsAskedItsSecretOnly() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions = new ScriptedQuestions("orders", "", "", API, NO_SECRET);

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(written(definitions).applicationType()).isEqualTo(ApplicationType.API);
        assertThat(questions.asked())
                .containsExactly("Client id", "Name", "Description", "Application type", "Client secret");
    }

    @Test
    void aHardCodedSecretIsAskedAgain() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions =
                new ScriptedQuestions("batch", "", "", SERVICE, A_VARIABLE, "s3cr3t", "{{batchSecret}}");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(written(definitions).secret()).contains(value("{{batchSecret}}"));
        assertThat(questions.told())
                .containsExactly("A client secret is a variable ({{name}}), never a value written in the file");
    }

    @Test
    void aGivenHardCodedSecretFailsWithoutAsking() {
        assertThatThrownBy(() -> new SecretAnswer.Variable(value("s3cr3t")))
                .isInstanceOf(HardCodedSecretException.class)
                .hasMessage("A client secret is a variable ({{name}}), never a value written in the file");
    }

    @Test
    void aSecretVariableWithADefaultIsAskedAgain() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions = new ScriptedQuestions(
                "batch", "", "", SERVICE, A_VARIABLE, "{{batchSecret:s3cr3t}}", "{{batchSecret}}");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(written(definitions).secret()).contains(value("{{batchSecret}}"));
        assertThat(questions.told()).containsExactly(NO_DEFAULT_MESSAGE);
    }

    @Test
    void aGivenSecretVariableWithADefaultFails() {
        assertThatThrownBy(() -> new SecretAnswer.Variable(value("{{batchSecret:s3cr3t}}")))
                .isInstanceOf(HardCodedSecretException.class)
                .hasMessage(NO_DEFAULT_MESSAGE);
        assertThatThrownBy(() -> new SecretAnswer.Variable(value("{{a}}{{b}}")))
                .isInstanceOf(HardCodedSecretException.class);
        assertThat(new SecretAnswer.Variable(value("{{batchSecret}}")).value()).isEqualTo(value("{{batchSecret}}"));
    }

    @Test
    void noneWritesNoSecret() {
        FakeRealmDefinitions definitions = realmWith("other");
        ScriptedQuestions questions = new ScriptedQuestions("batch", "", "", SERVICE, NO_SECRET);

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(written(definitions).secret()).isEmpty();
        assertThat(questions.asked()).doesNotContain("Secret variable");
    }

    @Test
    void anExistingIdIsAskedAgain() {
        FakeRealmDefinitions definitions = realmWith("frontend");
        ScriptedQuestions questions = new ScriptedQuestions("frontend", "front2", "", "", API, NO_SECRET);

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(written(definitions).clientId()).isEqualTo(value("front2"));
        assertThat(questions.told()).containsExactly("Client 'frontend' already exists");
    }

    @Test
    void anExistingGivenIdFails() {
        FakeRealmDefinitions definitions = realmWith("frontend");
        ClientAnswers given = new ClientAnswers(
                Optional.of(value("frontend")),
                Optional.of(value("Front")),
                Optional.of(value("The front")),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false);

        assertThatThrownBy(() -> useCase(definitions).create("demo", given, new ScriptedQuestions()))
                .isInstanceOf(ClientExistsException.class)
                .hasMessage("Client 'frontend' already exists");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void givenAnswersAreNotAsked() {
        FakeRealmDefinitions definitions = realmWith("other");
        ClientAnswers given = new ClientAnswers(
                Optional.of(value("shop")),
                Optional.of(value("Shop")),
                Optional.of(value("The shop")),
                Optional.of(ApplicationType.WEB_APPLICATION),
                Optional.of(value("https://shop")),
                Optional.of(List.of(value("https://shop/cb"))),
                Optional.of(List.of()),
                Optional.of(new SecretAnswer.Variable(value("{{shopSecret}}"))),
                false);
        ScriptedQuestions questions = new ScriptedQuestions();

        useCase(definitions).create("demo", given, questions);

        ClientDefinition client = written(definitions);
        assertThat(client.clientId()).isEqualTo(value("shop"));
        assertThat(client.name()).contains(value("Shop"));
        assertThat(client.redirectUris()).containsExactly(value("https://shop/cb"));
        assertThat(client.webOrigins()).isEmpty();
        assertThat(client.secret()).contains(value("{{shopSecret}}"));
        assertThat(questions.asked()).isEmpty();
    }

    @Test
    void theFirstClientNeedsAConfirmation() {
        FakeRealmDefinitions definitions = realmWith();
        ScriptedQuestions questions = new ScriptedQuestions("orders", "", "", API, NO_SECRET, "n");

        assertThatThrownBy(() -> useCase(definitions).create("demo", ClientAnswers.none(), questions))
                .isInstanceOf(FirstDeclarationException.class);
        assertThat(definitions.saved).isEmpty();
        assertThat(questions.asked()).endsWith("Client secret", FirstDeclarationException.warning(existing("orders")));
    }

    @Test
    void theFirstClientIsWrittenOnceConfirmed() {
        FakeRealmDefinitions definitions = realmWith();
        ScriptedQuestions questions = new ScriptedQuestions("orders", "", "", API, NO_SECRET, "y");

        useCase(definitions).create("demo", ClientAnswers.none(), questions);

        assertThat(definitions.saved).hasSize(1);
    }

    @Test
    void aGivenConfirmationIsNotAsked() {
        FakeRealmDefinitions definitions = realmWith();
        ClientAnswers given = new ClientAnswers(
                Optional.of(value("orders")),
                Optional.empty(),
                Optional.empty(),
                Optional.of(ApplicationType.API),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new SecretAnswer.None()),
                true);

        useCase(definitions).create("demo", given, new ScriptedQuestions("", ""));

        assertThat(definitions.saved).hasSize(1);
    }

    @Test
    void anUnknownRealmFails() {
        assertThatThrownBy(() -> useCase(realmWith()).create("nope", ClientAnswers.none(), new ScriptedQuestions()))
                .isInstanceOf(UnknownRealmFolderException.class);
    }

    @Test
    void aSecretGivenForASpaIsRefused() {
        FakeRealmDefinitions definitions = realmWith("other");
        ClientAnswers answers = new ClientAnswers(
                Optional.of(value("frontend")),
                Optional.of(value("Front")),
                Optional.of(value("The front")),
                Optional.of(ApplicationType.SPA),
                Optional.of(value("https://a")),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new SecretAnswer.Variable(value("{{frontSecret}}"))),
                true);

        assertThatThrownBy(() -> useCase(definitions).create("demo", answers, new ScriptedQuestions()))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("A front-end application cannot keep a secret");
        assertThat(definitions.saved).isEmpty();
    }
}
