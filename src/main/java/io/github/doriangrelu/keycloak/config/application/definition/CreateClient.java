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
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates a client by guided questions (ADR-008): the use case chooses the questions and what to propose, the
 * {@link Questions} port asks them, and {@link SaveElement} writes the client under the configuration rules.
 */
public final class CreateClient {

    private final RealmDefinitions definitions;
    private final SaveElement saveElement;

    public CreateClient(final RealmDefinitions definitions, final SaveElement saveElement) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.saveElement = Objects.requireNonNull(saveElement, "saveElement");
    }

    /**
     * @param given the answers already given, which are not asked
     * @return the file written, with what the rules say about the client
     * @throws UnknownRealmFolderException when the folder is not a realm of the shell
     * @throws ClientExistsException when the given id is taken by a client of the realm
     * @throws FirstDeclarationException when the client is the first of the realm and this is not confirmed
     * @throws RuleViolationException when the client fails a required rule: nothing is written
     * @throws MissingAnswerException when an answer is missing and cannot be asked
     */
    public SaveResult create(final String folder, final ClientAnswers given, final Questions questions) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        TemplatedValue clientId = clientId(realm, given, questions);
        Optional<TemplatedValue> name = given.name().or(() -> questions.askOptionalValue("Name", Optional.empty()));
        Optional<TemplatedValue> description =
                given.description().or(() -> questions.askOptionalValue("Description", Optional.empty()));
        ApplicationType type = given.type()
                .orElseGet(() -> questions.choose("Application type", ClientQuestions.TYPES, Optional.empty()));
        if (type == ApplicationType.SPA && given.secret().isPresent()) {
            throw SecretNotAllowedException.forSpa();
        }
        boolean hasUrl = type == ApplicationType.SPA || type == ApplicationType.WEB_APPLICATION;
        Optional<TemplatedValue> url = hasUrl
                        && (given.redirectUris().isEmpty() || given.webOrigins().isEmpty())
                ? Optional.of(given.url().orElseGet(() -> questions.askValue("Application URL", Optional.empty())))
                : Optional.empty();
        List<TemplatedValue> redirectUris = given.redirectUris()
                .orElseGet(() -> url.map(value ->
                                questions.askValues("Redirect URIs", List.of(TemplatedValue.of(value.raw() + "/*"))))
                        .orElse(List.of()));
        List<TemplatedValue> webOrigins = given.webOrigins()
                .orElseGet(() -> url.map(value -> questions.askValues(
                                "Web origins", type == ApplicationType.SPA ? List.of(value) : List.of()))
                        .orElse(List.of()));
        Optional<TemplatedValue> secret = Optional.empty();
        if (type != ApplicationType.SPA) {
            SecretAnswer answer = given.secret()
                    .orElseGet(
                            () -> ClientQuestions.askSecret(clientId, Optional.empty(), Optional.empty(), questions));
            secret =
                    answer instanceof SecretAnswer.Variable variable ? Optional.of(variable.value()) : Optional.empty();
        }
        ClientDefinition client =
                new ClientDefinition(clientId, name, description, type, redirectUris, webOrigins, Map.of(), secret);
        boolean declareType = given.declareType()
                || realm.declares(client)
                || questions.confirm(FirstDeclarationException.warning(client), false);
        return this.saveElement.save(folder, client, Optional.empty(), declareType);
    }

    private static TemplatedValue clientId(
            final RealmDefinition realm, final ClientAnswers given, final Questions questions) {
        if (given.clientId().isPresent()) {
            TemplatedValue clientId = given.clientId().get();
            if (realm.client(clientId.raw()).isPresent()) {
                throw new ClientExistsException(clientId.raw());
            }
            return clientId;
        }
        while (true) {
            TemplatedValue clientId = questions.askValue("Client id", Optional.empty());
            if (realm.client(clientId.raw()).isEmpty()) {
                return clientId;
            }
            questions.tell("Client '" + clientId.raw() + "' already exists");
        }
    }
}
