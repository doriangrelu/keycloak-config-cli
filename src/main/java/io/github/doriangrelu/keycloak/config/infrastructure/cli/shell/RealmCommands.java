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

import io.github.doriangrelu.keycloak.config.application.definition.Choice;
import io.github.doriangrelu.keycloak.config.application.definition.CreateRealm;
import io.github.doriangrelu.keycloak.config.application.definition.FormatRealm;
import io.github.doriangrelu.keycloak.config.application.definition.InvalidRealmFolderException;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RealmFolderExistsException;
import io.github.doriangrelu.keycloak.config.application.definition.UnknownFormatException;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/** Lists, opens and shows the realms of the root (ADR-008). */
public class RealmCommands {

    private final ReadRealms readRealms;
    private final FormatRealm formatRealm;
    private final CreateRealm createRealm;
    private final ShellSession session;

    public RealmCommands(
            final ReadRealms readRealms,
            final FormatRealm formatRealm,
            final CreateRealm createRealm,
            final ShellSession session) {
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.formatRealm = Objects.requireNonNull(formatRealm, "formatRealm");
        this.createRealm = Objects.requireNonNull(createRealm, "createRealm");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Command(
            name = {"realm", "create"},
            group = "Realms",
            description =
                    "Create a realm folder with its realm file, and open it: what the options do not give is asked")
    public String create(
            final CommandContext context,
            @Option(longName = "folder", description = "the folder of the realm", required = false)
                    final String folderOption,
            @Option(longName = "realm", description = "the name of the realm, placeholders allowed", required = false)
                    final String realmOption,
            @Option(longName = "format", description = "yaml or json", required = false) final String formatOption) {
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        Optional<DefinitionFormat> format = given(formatOption).map(RealmCommands::formatOf);
        String folder = folderOf(folderOption, questions, context.outputWriter());
        TemplatedValue realm = given(realmOption)
                .map(TemplatedValue::of)
                .orElseGet(() -> questions.askValue("Name of the realm", Optional.of(TemplatedValue.of(folder))));
        DefinitionFormat chosen = format.orElseGet(() -> questions.choose(
                "Format of the files",
                List.of(
                        new Choice<>(DefinitionFormat.YAML, "YAML", "readable, accepts comments"),
                        new Choice<>(DefinitionFormat.JSON, "JSON", "no comment")),
                Optional.empty()));
        String file = this.createRealm.create(folder, realm, chosen);
        this.session.open(folder);
        return "Realm '%s' created: %s/%s".formatted(folder, folder, file);
    }

    private String folderOf(final String option, final Questions questions, final PrintWriter writer) {
        Optional<String> given = given(option);
        if (given.isPresent()) {
            this.createRealm.checkFolder(given.get());
            return given.get();
        }
        while (true) {
            String answer = questions.ask("Folder of the realm", Optional.empty());
            try {
                this.createRealm.checkFolder(answer);
                return answer;
            } catch (InvalidRealmFolderException | RealmFolderExistsException e) {
                writer.println("  " + e.getMessage());
            }
        }
    }

    private static Optional<String> given(final String option) {
        return Optional.ofNullable(option).map(String::strip).filter(value -> !value.isEmpty());
    }

    private static DefinitionFormat formatOf(final String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "yaml" -> DefinitionFormat.YAML;
            case "json" -> DefinitionFormat.JSON;
            default -> throw new UnknownFormatException(format);
        };
    }

    @Command(
            name = {"realm", "format"},
            group = "Realms",
            description = "Write the files of the opened realm in the layout of the shell, content unchanged:"
                    + " run it once and commit it apart")
    public String format() {
        List<String> changed = this.formatRealm.format(this.session.requireOpenedRealm());
        if (changed.isEmpty()) {
            return "Already formatted";
        }
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        changed.forEach(file -> lines.add("Formatted: " + file));
        return lines.toString();
    }

    @Command(
            name = {"realm", "list"},
            group = "Realms",
            description = "List the realm folders of the root")
    public String list() {
        List<String> folders = this.readRealms.list();
        return folders.isEmpty() ? "No realm folder under the root" : String.join(System.lineSeparator(), folders);
    }

    @Command(
            name = {"realm", "open"},
            group = "Realms",
            description = "Open a realm: the next commands work on it")
    public String open(@Argument(index = 0, description = "the folder of the realm") final String folder) {
        RealmDefinition realm = this.readRealms.open(folder);
        this.session.open(folder);
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        lines.add("Realm '%s' opened (%d clients, %d roles, %d groups)"
                .formatted(
                        folder,
                        realm.clients().size(),
                        realm.roles().size(),
                        realm.groups().size()));
        for (UnreadableFile file : realm.unreadableFiles()) {
            lines.add("%s:%d cannot be read before rendering: %s. The realm is read-only."
                    .formatted(file.file(), file.line(), file.reason()));
        }
        return lines.toString();
    }

    @Command(
            name = {"realm", "show"},
            group = "Realms",
            description = "Show the elements of the opened realm")
    public String show() {
        RealmDefinition realm = this.readRealms.open(this.session.requireOpenedRealm());
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        lines.add("Clients:");
        for (Defined<ClientDefinition> client : realm.clients()) {
            lines.add("  %s (%s) — %s"
                    .formatted(
                            client.definition().clientId().raw(),
                            client.definition().applicationType(),
                            where(client.origin())));
        }
        lines.add("Roles:");
        for (Defined<RoleDefinition> role : realm.roles()) {
            String owner = role.definition()
                    .clientId()
                    .map(client -> "client " + client.raw())
                    .orElse("realm");
            lines.add("  %s (%s) — %s".formatted(role.definition().name().raw(), owner, where(role.origin())));
        }
        lines.add("Groups:");
        for (Defined<GroupDefinition> group : realm.groups()) {
            lines.add("  %s — %s".formatted(group.definition().path(), where(group.origin())));
        }
        return lines.toString();
    }

    private static String where(final Origin origin) {
        return origin.file() + ":" + origin.line();
    }
}
