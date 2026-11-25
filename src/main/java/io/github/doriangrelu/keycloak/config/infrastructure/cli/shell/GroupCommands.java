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

import io.github.doriangrelu.keycloak.config.application.definition.CreateGroup;
import io.github.doriangrelu.keycloak.config.application.definition.EditGroup;
import io.github.doriangrelu.keycloak.config.application.definition.GroupAnswers;
import io.github.doriangrelu.keycloak.config.application.definition.GroupMovedException;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.SaveResult;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/** Creates, edits and lists the groups of the opened realm (ADR-008): the options answer the guided questions. */
public class GroupCommands {

    private final CreateGroup createGroup;
    private final EditGroup editGroup;
    private final ReadRealms readRealms;
    private final ShellSession session;

    public GroupCommands(
            final CreateGroup createGroup,
            final EditGroup editGroup,
            final ReadRealms readRealms,
            final ShellSession session) {
        this.createGroup = Objects.requireNonNull(createGroup, "createGroup");
        this.editGroup = Objects.requireNonNull(editGroup, "editGroup");
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Command(
            name = {"group", "create"},
            group = "Groups",
            description = "Create a group, at the top or under another: what the options do not give is asked")
    public String create(
            final CommandContext context,
            @Option(longName = "name", description = "the name, placeholders allowed", required = false)
                    final String name,
            @Option(
                            longName = "parent",
                            description = "the path of the parent group, such as /team; not given for a top-level"
                                    + " group, which asks it when groups exist",
                            required = false)
                    final String parent,
            @Option(
                            longName = "realm-roles",
                            description = "the realm roles to give, separated by commas; none for no role",
                            required = false)
                    final String realmRoles,
            @Option(
                            longName = "client-roles",
                            description = "the client roles to give as client/role, separated by commas; none for no"
                                    + " role",
                            required = false)
                    final String clientRoles,
            @Option(
                            longName = "declare-type",
                            description = "confirm writing the first group of the realm",
                            required = false,
                            defaultValue = "false")
                    final boolean declareType) {
        String folder = this.session.requireOpenedRealm();
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        GroupAnswers answers =
                new GroupAnswers(value(name), given(parent), list(realmRoles), list(clientRoles), declareType);
        Set<String> before = paths(folder);
        SaveResult result = this.createGroup.create(folder, answers, questions);
        String created = value(name)
                .map(given -> given(parent).orElse("") + "/" + given.raw())
                .orElseGet(() -> newGroup(folder, before));
        return String.join(System.lineSeparator(), SaveResultLines.of(folder, "Group '" + created + "'", result));
    }

    @Command(
            name = {"group", "edit"},
            group = "Groups",
            description = "Edit the roles of a group of the opened realm: the current ones are proposed")
    public String edit(
            final CommandContext context,
            @Argument(index = 0, description = "the path of the group, such as /team/admins") final String path,
            @Option(longName = "name", description = "refused: a group is not renamed", required = false)
                    final String name,
            @Option(longName = "parent", description = "refused: a group is not moved", required = false)
                    final String parent,
            @Option(
                            longName = "realm-roles",
                            description = "the realm roles to give, separated by commas; none for no role",
                            required = false)
                    final String realmRoles,
            @Option(
                            longName = "client-roles",
                            description = "the client roles to give as client/role, separated by commas; none for no"
                                    + " role",
                            required = false)
                    final String clientRoles) {
        if (given(name).isPresent() || given(parent).isPresent()) {
            throw new GroupMovedException();
        }
        String folder = this.session.requireOpenedRealm();
        Questions questions = new ConsoleQuestions(context.inputReader(), context.outputWriter());
        GroupAnswers answers =
                new GroupAnswers(Optional.empty(), Optional.empty(), list(realmRoles), list(clientRoles), false);
        SaveResult result = this.editGroup.edit(folder, path, answers, questions);
        return String.join(System.lineSeparator(), SaveResultLines.of(folder, "Group '" + path + "'", result));
    }

    @Command(
            name = {"group", "list"},
            group = "Groups",
            description = "List the groups of the opened realm as a tree, with their roles")
    public String list() {
        List<Defined<GroupDefinition>> groups =
                this.readRealms.open(this.session.requireOpenedRealm()).groups();
        if (groups.isEmpty()) {
            return "No group";
        }
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        children(groups, "").forEach(group -> add(lines, groups, group, 0));
        return lines.toString();
    }

    private static void add(
            final StringJoiner lines,
            final List<Defined<GroupDefinition>> all,
            final Defined<GroupDefinition> group,
            final int depth) {
        GroupDefinition definition = group.definition();
        lines.add("%s%s%s  %s:%d"
                .formatted(
                        "  ".repeat(depth),
                        definition.name().raw(),
                        roles(definition),
                        group.origin().file(),
                        group.origin().line()));
        children(all, definition.path()).forEach(child -> add(lines, all, child, depth + 1));
    }

    /** The groups directly under a path, sorted by name; the empty path gives the top-level groups. */
    private static List<Defined<GroupDefinition>> children(
            final List<Defined<GroupDefinition>> all, final String parent) {
        return all.stream()
                .filter(group -> {
                    String path = group.definition().path();
                    int slash = path.lastIndexOf('/');
                    return slash >= 0 && path.substring(0, slash).equals(parent);
                })
                .sorted(Comparator.comparing(group -> group.definition().name().raw()))
                .toList();
    }

    private static String roles(final GroupDefinition group) {
        List<String> parts = new ArrayList<>();
        if (!group.realmRoles().isEmpty()) {
            parts.add("realm: " + String.join(", ", new TreeSet<>(group.realmRoles())));
        }
        new TreeMap<>(group.clientRoles()).forEach((client, names) -> {
            if (!names.isEmpty()) {
                parts.add(client + ": " + String.join(", ", new TreeSet<>(names)));
            }
        });
        return parts.isEmpty() ? "" : " [" + String.join("; ", parts) + "]";
    }

    private Set<String> paths(final String folder) {
        return this.readRealms.open(folder).groups().stream()
                .map(group -> group.definition().path())
                .collect(Collectors.toSet());
    }

    /** The path of the group asked during the creation: the one that was not in the realm before. */
    private String newGroup(final String folder, final Set<String> before) {
        return this.readRealms.open(folder).groups().stream()
                .map(group -> group.definition().path())
                .filter(path -> !before.contains(path))
                .findFirst()
                .orElse("?");
    }

    private static Optional<String> given(final String option) {
        return Optional.ofNullable(option).map(String::strip).filter(text -> !text.isEmpty());
    }

    private static Optional<TemplatedValue> value(final String option) {
        return given(option).map(TemplatedValue::of);
    }

    /** Comma-separated values; {@code none} or {@code -} is the empty list, an absent option is not given. */
    private static Optional<List<String>> list(final String option) {
        Optional<String> text = given(option);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String bare = text.get().replaceAll("^[\"']+|[\"']+$", "").strip();
        if (bare.equalsIgnoreCase("none") || bare.equals("-")) {
            return Optional.of(List.of());
        }
        return Optional.of(Arrays.stream(text.get().split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList());
    }
}
