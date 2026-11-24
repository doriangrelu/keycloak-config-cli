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

import io.github.doriangrelu.keycloak.config.application.definition.ListVariables;
import io.github.doriangrelu.keycloak.config.domain.definition.VariableUse;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.shell.core.command.annotation.Command;

/** The variables of the opened realm: the placeholders of its files. */
public class VariableCommands {

    private final ListVariables listVariables;
    private final ShellSession session;

    public VariableCommands(final ListVariables listVariables, final ShellSession session) {
        this.listVariables = Objects.requireNonNull(listVariables, "listVariables");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Command(
            name = {"variables", "list"},
            group = "Variables",
            description = "List the variables of the opened realm, with their default values and files")
    public String list() {
        List<VariableUse> uses = this.listVariables.list(this.session.requireOpenedRealm());
        if (uses.isEmpty()) {
            return "No variable";
        }
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        uses.forEach(use ->
                lines.add("%s  %s  %s".formatted(use.name(), defaults(use), String.join(", ", use.files()))));
        return lines.toString();
    }

    private static String defaults(final VariableUse use) {
        if (use.defaultValues().isEmpty()) {
            return "no default";
        }
        if (use.hasConflictingDefaults()) {
            return "defaults differ: " + String.join(", ", use.defaultValues());
        }
        return "default: " + use.defaultValues().getFirst();
    }
}
