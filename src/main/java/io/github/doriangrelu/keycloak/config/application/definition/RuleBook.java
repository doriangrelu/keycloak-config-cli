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

import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakBuiltIns;
import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleEngine;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleSet;
import java.util.List;
import java.util.Objects;

/**
 * The configuration rules in force (ADR-009): the base rules, the Java rules and those of the organisation, loaded once
 * at first use. A file that is not valid fails every use: a broken state is never kept as a success.
 */
public final class RuleBook {

    private final RuleFiles files;
    private final List<Rule> javaRules;
    private final KeycloakBuiltIns builtIns;
    private RuleSet rules;
    private RuleEngine engine;

    public RuleBook(final RuleFiles files, final List<Rule> javaRules, final KeycloakBuiltIns builtIns) {
        this.files = Objects.requireNonNull(files, "files");
        this.javaRules = List.copyOf(javaRules);
        this.builtIns = Objects.requireNonNull(builtIns, "builtIns");
    }

    /**
     * @throws io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException when a file is
     *     invalid
     */
    public synchronized RuleSet rules() {
        if (this.rules == null) {
            this.rules = RuleSet.of(this.files.base(), this.javaRules, this.files.organisation());
        }
        return this.rules;
    }

    public synchronized RuleEngine engine() {
        if (this.engine == null) {
            this.engine = new RuleEngine(rules());
        }
        return this.engine;
    }

    public BuiltIns builtIns() {
        return this.builtIns.builtIns();
    }
}
