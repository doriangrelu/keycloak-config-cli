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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Applies the rules to an element: the defaults to propose, the verdict on an edit, the report on an element. */
public final class RuleEngine {

    private final RuleSet rules;

    public RuleEngine(final RuleSet rules) {
        this.rules = rules;
    }

    /**
     * The values the active rules propose for an element, without the keys it already has. For a same key the last
     * rule in the order of the rule set wins.
     */
    public ElementKeys defaults(final RuleElement element, final ElementKeys keys) {
        Map<KeyPath, KeyValue> proposed = new LinkedHashMap<>();
        for (Rule rule : active(element, identifierOf(element, keys))) {
            if (rule.appliesTo(keys)) {
                proposed.putAll(rule.defaults().values());
            }
        }
        proposed.keySet().removeAll(keys.values().keySet());
        return new ElementKeys(proposed);
    }

    /**
     * Checks an edit. A required failure blocks when the edit introduces it: the element is new, or the rule did not
     * apply to the element before, or it did not fail. Otherwise it is only a reminder.
     */
    public Verdict check(
            final RuleElement element,
            final Optional<ElementKeys> before,
            final ElementKeys after,
            final RealmContext context) {
        List<Finding> blocking = new ArrayList<>();
        List<Finding> reminders = new ArrayList<>();
        List<Finding> advice = new ArrayList<>();
        List<Finding> unverifiable = new ArrayList<>();
        for (Rule rule : active(element, identifierOf(element, after))) {
            if (!rule.appliesTo(after)) {
                continue;
            }
            Outcome outcome = rule.check(after, context);
            Finding finding = new Finding(rule.id(), rule.severity(), rule.message(), outcome);
            if (outcome == Outcome.UNVERIFIABLE) {
                unverifiable.add(finding);
            } else if (outcome == Outcome.FAILED && rule.severity() == Severity.ADVICE) {
                advice.add(finding);
            } else if (outcome == Outcome.FAILED) {
                if (failedBefore(rule, before, context)) {
                    reminders.add(finding);
                } else {
                    blocking.add(finding);
                }
            }
        }
        return new Verdict(blocking, reminders, advice, unverifiable);
    }

    /** The non-conformities of an element as it is, the required ones first. */
    public List<Finding> report(final RuleElement element, final ElementKeys keys, final RealmContext context) {
        List<Finding> required = new ArrayList<>();
        List<Finding> advice = new ArrayList<>();
        for (Rule rule : active(element, identifierOf(element, keys))) {
            if (rule.appliesTo(keys) && rule.check(keys, context) == Outcome.FAILED) {
                Finding finding = new Finding(rule.id(), rule.severity(), rule.message(), Outcome.FAILED);
                (rule.severity() == Severity.REQUIRED ? required : advice).add(finding);
            }
        }
        required.addAll(advice);
        return List.copyOf(required);
    }

    private static boolean failedBefore(
            final Rule rule, final Optional<ElementKeys> before, final RealmContext context) {
        return before.isPresent()
                && rule.appliesTo(before.get())
                && rule.check(before.get(), context) == Outcome.FAILED;
    }

    private List<Rule> active(final RuleElement element, final Optional<String> elementId) {
        return this.rules.rules().stream()
                .filter(rule -> rule.element() == element)
                .filter(rule -> !this.rules.isDisabled(rule.id(), elementId))
                .toList();
    }

    private static Optional<String> identifierOf(final RuleElement element, final ElementKeys keys) {
        return keys.get(element.identifierKey())
                .filter(KeyValue.Scalar.class::isInstance)
                .map(value -> ((KeyValue.Scalar) value).value().raw());
    }
}
