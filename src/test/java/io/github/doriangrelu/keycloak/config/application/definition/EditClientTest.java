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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EditClientTest {

    private static final String SPA = "1";
    private static final String WEB = "2";
    private static final String A_VARIABLE = "1";

    private static TemplatedValue value(final String raw) {
        return TemplatedValue.of(raw);
    }

    private static ClientDefinition shop() {
        return new ClientDefinition(
                value("shop"),
                Optional.of(value("Shop")),
                Optional.of(value("The shop")),
                ApplicationType.WEB_APPLICATION,
                List.of(value("https://shop/*")),
                List.of(value("https://shop")),
                Map.of("custom.key", value("kept")),
                Optional.of(value("{{shopSecret}}")));
    }

    private static ClientDefinition bare(final String id, final ApplicationType type) {
        return new ClientDefinition(
                value(id), Optional.empty(), Optional.empty(), type, List.of(), List.of(), Map.of(), Optional.empty());
    }

    private static FakeRealmDefinitions realmWith(final ClientDefinition client) {
        return new FakeRealmDefinitions(new RealmDefinition(
                "demo",
                Optional.empty(),
                List.of(new Defined<>(client, new Origin("20-clients.yaml", 3))),
                List.of(),
                List.of(),
                Set.of(ElementType.CLIENT),
                List.of()));
    }

    private static EditClient useCase(final FakeRealmDefinitions definitions) {
        return new EditClient(definitions, new SaveElement(definitions, Rules.none()));
    }

    private static ClientDefinition written(final FakeRealmDefinitions definitions) {
        assertThat(definitions.saved).hasSize(1);
        return (ClientDefinition) definitions.saved.getFirst();
    }

    private static ClientAnswers given(final ApplicationType type) {
        return new ClientAnswers(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(type),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false);
    }

    @Test
    void currentValuesAreProposed() {
        FakeRealmDefinitions definitions = realmWith(shop());
        ScriptedQuestions questions = new ScriptedQuestions("", "", "", "", "", "", "");

        useCase(definitions).edit("demo", "shop", ClientAnswers.none(), questions);

        assertThat(written(definitions)).isEqualTo(shop());
        assertThat(questions.asked())
                .containsExactly(
                        "Name",
                        "Description",
                        "Application type",
                        "Redirect URIs",
                        "Web origins",
                        "Client secret",
                        "Secret variable");
    }

    @Test
    void theAnswersReplaceTheCurrentValues() {
        FakeRealmDefinitions definitions = realmWith(shop());
        ScriptedQuestions questions =
                new ScriptedQuestions("-", "Sells", "", "https://a/*, https://b/*", "-", A_VARIABLE, "{{otherSecret}}");

        useCase(definitions).edit("demo", "shop", ClientAnswers.none(), questions);

        ClientDefinition client = written(definitions);
        assertThat(client.name()).isEmpty();
        assertThat(client.description()).contains(value("Sells"));
        assertThat(client.redirectUris()).containsExactly(value("https://a/*"), value("https://b/*"));
        assertThat(client.webOrigins()).isEmpty();
        assertThat(client.secret()).contains(value("{{otherSecret}}"));
    }

    @Test
    void changingTheTypeToSpaRewritesTheFlows() {
        FakeRealmDefinitions definitions = realmWith(shop());
        ScriptedQuestions questions = new ScriptedQuestions("", "", SPA, "", "");

        useCase(definitions).edit("demo", "shop", ClientAnswers.none(), questions);

        ClientDefinition client = written(definitions);
        assertThat(client.applicationType()).isEqualTo(ApplicationType.SPA);
        assertThat(client.secret()).isEmpty();
        assertThat(questions.asked()).doesNotContain("Client secret", "Secret variable");
    }

    @Test
    void aSpaBecomingAWebApplicationIsProposedTheVariable() {
        ClientDefinition spa = new ClientDefinition(
                value("my-front"),
                Optional.empty(),
                Optional.empty(),
                ApplicationType.SPA,
                List.of(value("https://f/*")),
                List.of(),
                Map.of(),
                Optional.empty());
        FakeRealmDefinitions definitions = realmWith(spa);
        ScriptedQuestions questions = new ScriptedQuestions("", "", WEB, "", "", A_VARIABLE, "");

        useCase(definitions).edit("demo", "my-front", ClientAnswers.none(), questions);

        assertThat(written(definitions).applicationType()).isEqualTo(ApplicationType.WEB_APPLICATION);
        assertThat(written(definitions).secret()).contains(value("{{myFrontSecret}}"));
    }

    @Test
    void aConfidentialClientWithoutSecretIsProposedNone() {
        ClientDefinition api = bare("orders", ApplicationType.API);
        FakeRealmDefinitions definitions = realmWith(api);
        ScriptedQuestions questions = new ScriptedQuestions("", "", "", "");

        useCase(definitions).edit("demo", "orders", ClientAnswers.none(), questions);

        assertThat(written(definitions)).isEqualTo(api);
        assertThat(questions.asked()).endsWith("Client secret");
    }

    @Test
    void aHardCodedSecretIsAskedAgain() {
        FakeRealmDefinitions definitions = realmWith(shop());
        ScriptedQuestions questions = new ScriptedQuestions("", "", "", "", "", A_VARIABLE, "s3cr3t", "{{shopSecret}}");

        useCase(definitions).edit("demo", "shop", ClientAnswers.none(), questions);

        assertThat(written(definitions).secret()).contains(value("{{shopSecret}}"));
        assertThat(questions.told()).containsExactly(HardCodedSecretException.MESSAGE);
    }

    @Test
    void aCurrentSecretWithADefaultIsNotProposedAgain() {
        ClientDefinition shop = shop();
        ClientDefinition withDefault = new ClientDefinition(
                shop.clientId(),
                shop.name(),
                shop.description(),
                shop.applicationType(),
                shop.redirectUris(),
                shop.webOrigins(),
                shop.attributes(),
                Optional.of(value("{{shopSecret:s3cr3t}}")));
        FakeRealmDefinitions definitions = realmWith(withDefault);
        ScriptedQuestions questions = new ScriptedQuestions("", "", "", "", "", A_VARIABLE, "");

        useCase(definitions).edit("demo", "shop", ClientAnswers.none(), questions);

        assertThat(written(definitions).secret()).contains(value("{{shopSecret}}"));
    }

    @Test
    void aSecretVariableWithADefaultIsAskedAgain() {
        FakeRealmDefinitions definitions = realmWith(shop());
        ScriptedQuestions questions =
                new ScriptedQuestions("", "", "", "", "", A_VARIABLE, "{{shopSecret:abc}}", "{{shopSecret}}");

        useCase(definitions).edit("demo", "shop", ClientAnswers.none(), questions);

        assertThat(written(definitions).secret()).contains(value("{{shopSecret}}"));
        assertThat(questions.told())
                .containsExactly(
                        "A secret variable has no default value: a default would be a secret written in the file");
    }

    @Test
    void aCustomClientIsAskedOnlyTheCommonFields() {
        ClientDefinition custom = new ClientDefinition(
                value("legacy"),
                Optional.empty(),
                Optional.of(value("Old")),
                ApplicationType.CUSTOM,
                List.of(value("https://l/*")),
                List.of(),
                Map.of("custom.key", value("kept")),
                Optional.of(value("{{legacySecret}}")));
        FakeRealmDefinitions definitions = realmWith(custom);
        ScriptedQuestions questions = new ScriptedQuestions("", "New", "", "");

        useCase(definitions).edit("demo", "legacy", ClientAnswers.none(), questions);

        assertThat(questions.asked()).containsExactly("Name", "Description", "Redirect URIs", "Web origins");
        ClientDefinition client = written(definitions);
        assertThat(client.description()).contains(value("New"));
        assertThat(client.applicationType()).isEqualTo(ApplicationType.CUSTOM);
        assertThat(client.secret()).contains(value("{{legacySecret}}"));
        assertThat(client.attributes()).containsEntry("custom.key", value("kept"));
    }

    @Test
    void aTypeGivenForACustomClientIsRefused() {
        FakeRealmDefinitions definitions = realmWith(bare("legacy", ApplicationType.CUSTOM));

        assertThatThrownBy(() -> useCase(definitions)
                        .edit("demo", "legacy", given(ApplicationType.SPA), new ScriptedQuestions()))
                .isInstanceOf(CustomClientTypeException.class)
                .hasMessageContaining("legacy");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void anUnknownClientFails() {
        FakeRealmDefinitions definitions = realmWith(shop());

        assertThatThrownBy(
                        () -> useCase(definitions).edit("demo", "nope", ClientAnswers.none(), new ScriptedQuestions()))
                .isInstanceOf(UnknownClientException.class)
                .hasMessage("Client 'nope' does not exist");
    }

    @Test
    void anUnknownRealmFails() {
        FakeRealmDefinitions definitions = realmWith(shop());

        assertThatThrownBy(
                        () -> useCase(definitions).edit("nope", "shop", ClientAnswers.none(), new ScriptedQuestions()))
                .isInstanceOf(UnknownRealmFolderException.class);
    }

    @Test
    void editingKeepsTheAttributesAndIgnoresAGivenId() {
        FakeRealmDefinitions definitions = realmWith(shop());
        ClientAnswers given = new ClientAnswers(
                Optional.of(value("other")),
                Optional.of(value("Renamed")),
                Optional.of(value("Desc")),
                Optional.of(ApplicationType.WEB_APPLICATION),
                Optional.of(value("https://ignored")),
                Optional.of(List.of(value("https://shop/cb"))),
                Optional.of(List.of()),
                Optional.of(new SecretAnswer.None()),
                false);
        ScriptedQuestions questions = new ScriptedQuestions();

        useCase(definitions).edit("demo", "shop", given, questions);

        ClientDefinition client = written(definitions);
        assertThat(client.clientId()).isEqualTo(value("shop"));
        assertThat(client.name()).contains(value("Renamed"));
        assertThat(client.redirectUris()).containsExactly(value("https://shop/cb"));
        assertThat(client.secret()).isEmpty();
        assertThat(client.attributes()).containsEntry("custom.key", value("kept"));
        assertThat(questions.asked()).isEmpty();
    }

    private static ClientAnswers givenSecret(final Optional<ApplicationType> type) {
        return new ClientAnswers(
                Optional.empty(),
                Optional.of(value("N")),
                Optional.of(value("D")),
                type,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new SecretAnswer.Variable(value("{{aSecret}}"))),
                false);
    }

    @Test
    void aSecretGivenForASpaIsRefused() {
        FakeRealmDefinitions definitions = realmWith(shop());

        assertThatThrownBy(() -> useCase(definitions)
                        .edit("demo", "shop", givenSecret(Optional.of(ApplicationType.SPA)), new ScriptedQuestions()))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("A front-end application cannot keep a secret");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void aSecretGivenForACustomClientIsRefused() {
        FakeRealmDefinitions definitions = realmWith(bare("legacy", ApplicationType.CUSTOM));

        assertThatThrownBy(() -> useCase(definitions)
                        .edit("demo", "legacy", givenSecret(Optional.empty()), new ScriptedQuestions()))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("The secret of a custom client is not edited by the shell");
        assertThat(definitions.saved).isEmpty();
    }
}
