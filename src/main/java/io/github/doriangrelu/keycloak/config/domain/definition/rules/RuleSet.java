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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** All the rules in force: the base rules, the Java rules and those of the organisation, with its disablements. */
public final class RuleSet {

    private final List<Rule> rules;
    private final List<Disablement> disablements;

    private RuleSet(final List<Rule> rules, final List<Disablement> disablements) {
        this.rules = rules;
        this.disablements = disablements;
    }

    public static RuleSet of(final List<Rule> base, final List<Rule> java, final OrganisationRules organisation) {
        List<Rule> all = new ArrayList<>(base);
        all.addAll(java);
        all.addAll(organisation.rules());
        Set<String> ids = new HashSet<>();
        for (Rule rule : all) {
            if (!ids.add(rule.id())) {
                throw new InvalidRulesException("Duplicate rule identifier '" + rule.id() + "'");
            }
        }
        for (Disablement disablement : organisation.disablements()) {
            if (!ids.contains(disablement.id())) {
                throw new InvalidRulesException("Unknown rule '" + disablement.id() + "' in disable");
            }
        }
        return new RuleSet(List.copyOf(all), organisation.disablements());
    }

    /** The rules in the order base, Java, organisation. */
    public List<Rule> rules() {
        return this.rules;
    }

    public List<Disablement> disablements() {
        return this.disablements;
    }

    /** Whether the rule is switched off for the element; an empty {@code for} covers every element. */
    public boolean isDisabled(final String ruleId, final String elementId) {
        return isDisabled(ruleId, Optional.ofNullable(elementId));
    }

    boolean isDisabled(final String ruleId, final Optional<String> elementId) {
        return this.disablements.stream()
                .anyMatch(disablement -> disablement.id().equals(ruleId) && disablement.covers(elementId));
    }
}
