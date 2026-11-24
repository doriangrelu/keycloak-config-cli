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

import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;

/** Lists, opens and shows the realms of the root (ADR-008). */
public class RealmCommands {

    private final ReadRealms readRealms;
    private final ShellSession session;

    public RealmCommands(final ReadRealms readRealms, final ShellSession session) {
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.session = Objects.requireNonNull(session, "session");
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
