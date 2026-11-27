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

import io.github.doriangrelu.keycloak.config.application.definition.CreateRole;
import io.github.doriangrelu.keycloak.config.application.definition.EditRole;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RoleAnswers;
import io.github.doriangrelu.keycloak.config.application.definition.RoleRenamedException;
import io.github.doriangrelu.keycloak.config.application.definition.SaveResult;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
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

/** Creates, edits and lists the roles of the opened realm (ADR-008): the options answer the guided questions. */
public class RoleCommands {

    /** The realm roles first, then the roles of each client by client id, each sorted by name. */
    private static final Comparator<Defined<RoleDefinition>> BY_PLACE = Comparator.comparing(
                    (Defined<RoleDefinition> role) ->
                            role.definition().clientId().isPresent())
            .thenComparing(role ->
                    role.definition().clientId().map(TemplatedValue::raw).orElse(""))
            .thenComparing(role -> role.definition().name().raw());

    private final CreateRole createRole;
    private final EditRole editRole;
    private final ReadRealms readRealms;
    private final ShellSession session;
    private final SessionDeclarationWarning declarationWarning;

    public RoleCommands(
            final CreateRole createRole,
            final EditRole editRole,
            final ReadRealms readRealms,
            final ShellSession session,
            final SessionDeclarationWarning declarationWarning) {
        this.createRole = Objects.requireNonNull(createRole, "createRole");
        this.editRole = Objects.requireNonNull(editRole, "editRole");
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.session = Objects.requireNonNull(session, "session");
        this.declarationWarning = Objects.requireNonNull(declarationWarning, "declarationWarning");
    }

    @Command(
            name = {"role", "create"},
            group = "Roles",
            description = "Create a realm role or a client role: what the options do not give is asked")
    public String create(
            final CommandContext context,
            @Option(
                            longName = "client",
                            description = "the id of the client owning the role; none for a realm role",
                            required = false)
                    final String client,
            @Option(longName = "name", description = "the name, placeholders allowed", required = false)
                    final String name,
            @Option(longName = "description", description = "the description, placeholders allowed", required = false)
                    final String description,
            @Option(
                            longName = "declare-type",
                            description = "confirm writing the first role of its place",
                            required = false,
                            defaultValue = "false")
                    final boolean declareType) {
        String folder = this.session.requireOpenedRealm();
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        RoleAnswers answers = new RoleAnswers(value(client), value(name), value(description), declareType);
        Set<String> before = roleKeys(folder);
        SaveResult result = this.createRole.create(folder, answers, questions, this.declarationWarning.of(questions));
        String created = value(name).map(TemplatedValue::raw).orElseGet(() -> newRole(folder, before));
        return String.join(System.lineSeparator(), SaveResultLines.of(folder, "Role '" + created + "'", result));
    }

    @Command(
            name = {"role", "edit"},
            group = "Roles",
            description = "Edit the description of a role of the opened realm: the current one is proposed")
    public String edit(
            final CommandContext context,
            @Argument(index = 0, description = "the name of the role") final String roleName,
            @Option(
                            longName = "client",
                            description = "the id of the client owning the role; none for a realm role",
                            required = false)
                    final String client,
            @Option(longName = "name", description = "refused: a role is not renamed", required = false)
                    final String name,
            @Option(longName = "description", description = "the description, placeholders allowed", required = false)
                    final String description) {
        if (given(name).isPresent()) {
            throw new RoleRenamedException();
        }
        String folder = this.session.requireOpenedRealm();
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        RoleAnswers answers = new RoleAnswers(Optional.empty(), Optional.empty(), value(description), false);
        SaveResult result = this.editRole.edit(folder, roleName, given(client), answers, questions);
        return String.join(System.lineSeparator(), SaveResultLines.of(folder, "Role '" + roleName + "'", result));
    }

    @Command(
            name = {"role", "list"},
            group = "Roles",
            description = "List the roles of the opened realm, the realm roles first, with their place")
    public String list() {
        List<Defined<RoleDefinition>> roles = this.readRealms.open(this.session.requireOpenedRealm()).roles().stream()
                .sorted(BY_PLACE)
                .toList();
        if (roles.isEmpty()) {
            return "No role";
        }
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        roles.forEach(role -> lines.add("%s  %s  %s:%d"
                .formatted(
                        role.definition().name().raw(),
                        role.definition()
                                .clientId()
                                .map(clientId -> "client " + clientId.raw())
                                .orElse("realm"),
                        role.origin().file(),
                        role.origin().line())));
        return lines.toString();
    }

    private Set<String> roleKeys(final String folder) {
        return this.readRealms.open(folder).roles().stream()
                .map(RoleCommands::key)
                .collect(Collectors.toSet());
    }

    /** The name asked during the creation: the role that was not in the realm before. */
    private String newRole(final String folder, final Set<String> before) {
        return this.readRealms.open(folder).roles().stream()
                .filter(role -> !before.contains(key(role)))
                .map(role -> role.definition().name().raw())
                .findFirst()
                .orElse("?");
    }

    private static String key(final Defined<RoleDefinition> role) {
        return role.definition().clientId().map(TemplatedValue::raw).orElse("") + "/"
                + role.definition().name().raw();
    }

    private static Optional<String> given(final String option) {
        return Optional.ofNullable(option).map(String::strip).filter(text -> !text.isEmpty());
    }

    private static Optional<TemplatedValue> value(final String option) {
        return given(option).map(TemplatedValue::of);
    }
}
