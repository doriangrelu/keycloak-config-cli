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

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import java.util.List;
import java.util.Optional;

/**
 * A rule written as data (ADR-009): the element and the application type it applies to, the conditions that select
 * the elements, the default values it proposes and the checks that an element must pass.
 *
 * @param id the identifier of the rule
 * @param element the kind of element
 * @param applicationType the application type a client must have, for a rule on clients only
 * @param conditions the checks that must pass for the rule to apply
 * @param defaults the values proposed for the keys an element does not write
 * @param checks the checks the element must pass
 * @param severity how the rule is reported
 * @param message why the rule exists and what to do about it
 */
public record DeclarativeRule(
        String id,
        RuleElement element,
        Optional<ApplicationType> applicationType,
        List<Check> conditions,
        ElementKeys defaults,
        List<Check> checks,
        Severity severity,
        String message)
        implements Rule {

    public DeclarativeRule {
        if (id == null || id.isBlank()) {
            throw new InvalidRulesException("A rule needs an identifier");
        }
        if (message == null || message.isBlank()) {
            throw new InvalidRulesException("The rule '" + id + "' needs a message");
        }
        conditions = List.copyOf(conditions);
        checks = List.copyOf(checks);
        if (defaults.values().isEmpty() && checks.isEmpty()) {
            throw new InvalidRulesException("The rule '" + id + "' needs defaults or checks");
        }
        if (applicationType.isPresent() && element != RuleElement.CLIENT) {
            throw new InvalidRulesException(
                    "The rule '" + id + "' gives an application type, which only applies to a client");
        }
    }

    @Override
    public boolean appliesTo(final ElementKeys keys) {
        if (this.applicationType.isPresent() && !this.applicationType.get().equals(applicationTypeOf(keys))) {
            return false;
        }
        return this.conditions.stream().allMatch(condition -> condition.evaluate(keys) == Outcome.PASSED);
    }

    @Override
    public Outcome check(final ElementKeys keys, final RealmContext context) {
        Outcome result = Outcome.PASSED;
        for (Check check : this.checks) {
            Outcome outcome = check.evaluate(keys);
            if (outcome == Outcome.FAILED) {
                return Outcome.FAILED;
            }
            if (outcome == Outcome.UNVERIFIABLE) {
                result = Outcome.UNVERIFIABLE;
            }
        }
        return result;
    }

    /** The application type of a client view, deduced as the editing model does (see {@link ApplicationType#deduce}). */
    public static ApplicationType applicationTypeOf(final ElementKeys keys) {
        return ApplicationType.deduce(flow -> keys.get(KeyPath.parse(flow)).map(value -> switch (value) {
            case KeyValue.Scalar scalar -> scalar.value().raw();
            // A flow that is not a plain value is not a boolean
            default -> "?";
        }));
    }
}
