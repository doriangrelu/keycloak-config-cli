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
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits a client by guided questions (ADR-008): every question proposes the current value, so an empty answer keeps it.
 * The id is never asked, and the attributes are kept. A custom client, whose flows match no application type, is only
 * asked its common fields: its type, flows and secret stay as they are.
 */
public final class EditClient {

    private final RealmDefinitions definitions;
    private final SaveElement saveElement;

    public EditClient(final RealmDefinitions definitions, final SaveElement saveElement) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.saveElement = Objects.requireNonNull(saveElement, "saveElement");
    }

    /**
     * @param given the answers already given, which are not asked; its id and URL are ignored
     * @return the file written, with what the rules say about the client
     * @throws UnknownRealmFolderException when the folder is not a realm of the shell
     * @throws UnknownClientException when no client of the realm has this id
     * @throws CustomClientTypeException when a type is given for a custom client
     * @throws RuleViolationException when the client fails a required rule: nothing is written
     * @throws MissingAnswerException when an answer is missing and cannot be asked
     */
    public SaveResult edit(
            final String folder, final String clientId, final ClientAnswers given, final Questions questions) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        Defined<ClientDefinition> existing =
                realm.client(clientId).orElseThrow(() -> new UnknownClientException(clientId));
        ClientDefinition current = existing.definition();
        boolean custom = current.applicationType() == ApplicationType.CUSTOM;
        if (custom && given.type().isPresent()) {
            throw new CustomClientTypeException(clientId);
        }
        Optional<TemplatedValue> name =
                given.name().or(() -> questions.askOptionalValue("Name (empty for none)", current.name()));
        Optional<TemplatedValue> description = given.description()
                .or(() -> questions.askOptionalValue("Description (empty for none)", current.description()));
        ApplicationType type = custom ? ApplicationType.CUSTOM : typeOf(current, given, questions);
        // The URIs make sense for a browser-facing type; another type is only asked them when it has some to change
        boolean asksUris = custom
                || type == ApplicationType.SPA
                || type == ApplicationType.WEB_APPLICATION
                || !current.redirectUris().isEmpty()
                || !current.webOrigins().isEmpty();
        List<TemplatedValue> redirectUris = given.redirectUris()
                .orElseGet(() -> asksUris
                        ? questions.askValues("Redirect URIs", current.redirectUris())
                        : current.redirectUris());
        List<TemplatedValue> webOrigins = given.webOrigins()
                .orElseGet(() ->
                        asksUris ? questions.askValues("Web origins", current.webOrigins()) : current.webOrigins());
        Optional<TemplatedValue> secret = current.secret();
        if (!custom) {
            secret = type == ApplicationType.SPA ? Optional.empty() : secretOf(current, given, questions);
        }
        ClientDefinition client = new ClientDefinition(
                current.clientId(), name, description, type, redirectUris, webOrigins, current.attributes(), secret);
        return this.saveElement.save(folder, client, Optional.of(existing.origin()), true);
    }

    private static ApplicationType typeOf(
            final ClientDefinition current, final ClientAnswers given, final Questions questions) {
        return given.type()
                .orElseGet(() -> questions.choose(
                        "Application type", ClientQuestions.TYPES, Optional.of(current.applicationType())));
    }

    private static Optional<TemplatedValue> secretOf(
            final ClientDefinition current, final ClientAnswers given, final Questions questions) {
        boolean wasConfidential = current.applicationType() != ApplicationType.SPA;
        Optional<Boolean> proposedChoice = current.secret().isPresent()
                ? Optional.of(true)
                : wasConfidential ? Optional.of(false) : Optional.empty();
        SecretAnswer answer = given.secret()
                .orElseGet(() ->
                        ClientQuestions.askSecret(current.clientId(), current.secret(), proposedChoice, questions));
        return answer instanceof SecretAnswer.Variable variable ? Optional.of(variable.value()) : Optional.empty();
    }
}
