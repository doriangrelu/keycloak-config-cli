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

package io.github.doriangrelu.keycloak.config.infrastructure.cli.shell;

import io.github.doriangrelu.keycloak.config.application.definition.ClientAnswers;
import io.github.doriangrelu.keycloak.config.application.definition.CreateClient;
import io.github.doriangrelu.keycloak.config.application.definition.EditClient;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.SaveResult;
import io.github.doriangrelu.keycloak.config.application.definition.SecretAnswer;
import io.github.doriangrelu.keycloak.config.application.definition.UnknownApplicationTypeException;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/** Creates, edits and lists the clients of the opened realm (ADR-008): the options answer the guided questions. */
public class ClientCommands {

    private static final String NONE = "-";

    private final CreateClient createClient;
    private final EditClient editClient;
    private final ReadRealms readRealms;
    private final ShellSession session;

    public ClientCommands(
            final CreateClient createClient,
            final EditClient editClient,
            final ReadRealms readRealms,
            final ShellSession session) {
        this.createClient = Objects.requireNonNull(createClient, "createClient");
        this.editClient = Objects.requireNonNull(editClient, "editClient");
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Command(
            name = {"client", "create"},
            group = "Clients",
            description = "Create a client in the opened realm: what the options do not give is asked")
    public String create(
            final CommandContext context,
            @Option(longName = "id", description = "the client id, placeholders allowed", required = false)
                    final String id,
            @Option(longName = "name", description = "the name, placeholders allowed", required = false)
                    final String name,
            @Option(longName = "description", description = "the description, placeholders allowed", required = false)
                    final String description,
            @Option(longName = "type", description = "spa, web-application, service or api", required = false)
                    final String type,
            @Option(
                            longName = "url",
                            description = "the URL of the application, placeholders allowed",
                            required = false)
                    final String url,
            @Option(
                            longName = "redirect-uris",
                            description = "the redirect URIs, separated by commas, - for none",
                            required = false)
                    final String redirectUris,
            @Option(
                            longName = "web-origins",
                            description = "the web origins, separated by commas, - for none",
                            required = false)
                    final String webOrigins,
            @Option(
                            longName = "secret",
                            description = "the secret as a variable such as {{shopSecret}}, or none",
                            required = false)
                    final String secret,
            @Option(
                            longName = "declare-type",
                            description = "confirm writing the first client of the realm",
                            required = false,
                            defaultValue = "false")
                    final boolean declareType) {
        String folder = this.session.requireOpenedRealm();
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        ClientAnswers answers = new ClientAnswers(
                value(id),
                value(name),
                value(description),
                typeOf(type),
                value(url),
                values(redirectUris),
                values(webOrigins),
                secretOf(secret),
                declareType);
        Set<String> before = clientIds(folder);
        SaveResult result = this.createClient.create(folder, answers, questions);
        String created = value(id).map(TemplatedValue::raw).orElseGet(() -> newClient(folder, before));
        return String.join(System.lineSeparator(), SaveResultLines.of(folder, "Client '" + created + "'", result));
    }

    @Command(
            name = {"client", "edit"},
            group = "Clients",
            description = "Edit a client of the opened realm: every question proposes the current value")
    public String edit(
            final CommandContext context,
            @Argument(index = 0, description = "the client id") final String clientId,
            @Option(longName = "name", description = "the name, placeholders allowed", required = false)
                    final String name,
            @Option(longName = "description", description = "the description, placeholders allowed", required = false)
                    final String description,
            @Option(longName = "type", description = "spa, web-application, service or api", required = false)
                    final String type,
            @Option(
                            longName = "redirect-uris",
                            description = "the redirect URIs, separated by commas, - for none",
                            required = false)
                    final String redirectUris,
            @Option(
                            longName = "web-origins",
                            description = "the web origins, separated by commas, - for none",
                            required = false)
                    final String webOrigins,
            @Option(
                            longName = "secret",
                            description = "the secret as a variable such as {{shopSecret}}, or none",
                            required = false)
                    final String secret) {
        String folder = this.session.requireOpenedRealm();
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        ClientAnswers answers = new ClientAnswers(
                Optional.empty(),
                value(name),
                value(description),
                typeOf(type),
                Optional.empty(),
                values(redirectUris),
                values(webOrigins),
                secretOf(secret),
                false);
        SaveResult result = this.editClient.edit(folder, clientId, answers, questions);
        return String.join(System.lineSeparator(), SaveResultLines.of(folder, "Client '" + clientId + "'", result));
    }

    @Command(
            name = {"client", "list"},
            group = "Clients",
            description = "List the clients of the opened realm, with their type and place")
    public String list() {
        List<Defined<ClientDefinition>> clients =
                this.readRealms.open(this.session.requireOpenedRealm()).clients().stream()
                        .sorted(Comparator.comparing(
                                client -> client.definition().clientId().raw()))
                        .toList();
        if (clients.isEmpty()) {
            return "No client";
        }
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        clients.forEach(client -> lines.add("%s  %s  %s:%d"
                .formatted(
                        client.definition().clientId().raw(),
                        client.definition().applicationType().optionName(),
                        client.origin().file(),
                        client.origin().line())));
        return lines.toString();
    }

    private Set<String> clientIds(final String folder) {
        return this.readRealms.open(folder).clients().stream()
                .map(client -> client.definition().clientId().raw())
                .collect(Collectors.toSet());
    }

    /** The id asked during the creation: the client that was not in the realm before. */
    private String newClient(final String folder, final Set<String> before) {
        return clientIds(folder).stream()
                .filter(clientId -> !before.contains(clientId))
                .findFirst()
                .orElse("?");
    }

    private static Optional<String> given(final String option) {
        return Optional.ofNullable(option).map(String::strip).filter(text -> !text.isEmpty());
    }

    private static Optional<TemplatedValue> value(final String option) {
        return given(option).map(TemplatedValue::of);
    }

    private static Optional<List<TemplatedValue>> values(final String option) {
        return given(option)
                .map(text -> NONE.equals(text)
                        ? List.<TemplatedValue>of()
                        : Arrays.stream(text.split(","))
                                .map(String::strip)
                                .filter(item -> !item.isEmpty())
                                .map(TemplatedValue::of)
                                .toList());
    }

    private static Optional<ApplicationType> typeOf(final String option) {
        return given(option)
                .map(text -> ApplicationType.ofOptionName(text)
                        .filter(type -> type != ApplicationType.CUSTOM)
                        .orElseThrow(() -> new UnknownApplicationTypeException(text)));
    }

    private static Optional<SecretAnswer> secretOf(final String option) {
        return given(option)
                .map(text -> "none".equalsIgnoreCase(text)
                        ? new SecretAnswer.None()
                        : new SecretAnswer.Variable(TemplatedValue.of(text)));
    }
}
