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

import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleSet;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.shell.core.command.annotation.Command;

/** The rules in force (ADR-009): they do not depend on an opened realm. */
public class RuleCommands {

    private final RuleBook ruleBook;

    public RuleCommands(final RuleBook ruleBook) {
        this.ruleBook = Objects.requireNonNull(ruleBook, "ruleBook");
    }

    @Command(
            name = {"rules", "list"},
            group = "Rules",
            description = "List the rules in force, and the ones the organisation switched off with their reason")
    public String list() {
        RuleSet rules = this.ruleBook.rules();
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        for (Rule rule : rules.rules()) {
            lines.add("%s  %s  %s  %s"
                    .formatted(
                            rule.id(),
                            rule.element().name().toLowerCase(Locale.ROOT),
                            rule.severity().name().toLowerCase(Locale.ROOT),
                            rule.message()));
        }
        DisabledRules.lines(rules.disablements()).forEach(lines::add);
        return lines.toString();
    }
}
