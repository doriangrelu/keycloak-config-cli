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
import io.github.doriangrelu.keycloak.config.domain.definition.ClientFlows;
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

    private static final KeyPath PUBLIC_CLIENT = KeyPath.parse("publicClient");
    private static final KeyPath STANDARD_FLOW = KeyPath.parse("standardFlowEnabled");
    private static final KeyPath IMPLICIT_FLOW = KeyPath.parse("implicitFlowEnabled");
    private static final KeyPath DIRECT_ACCESS_GRANTS = KeyPath.parse("directAccessGrantsEnabled");
    private static final KeyPath SERVICE_ACCOUNTS = KeyPath.parse("serviceAccountsEnabled");

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
        if (this.applicationType.isPresent() && !applicationTypeOf(keys).equals(this.applicationType)) {
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

    /** The application type of a client view; empty when a flow key is missing, templated or not a boolean. */
    public static Optional<ApplicationType> applicationTypeOf(final ElementKeys keys) {
        return flowsOf(keys).map(ApplicationType::of);
    }

    /** The flows of a client view; empty when one is missing, templated or not a boolean. */
    private static Optional<ClientFlows> flowsOf(final ElementKeys keys) {
        Optional<Boolean> publicClient = flag(keys, PUBLIC_CLIENT);
        Optional<Boolean> standardFlow = flag(keys, STANDARD_FLOW);
        Optional<Boolean> implicitFlow = flag(keys, IMPLICIT_FLOW);
        Optional<Boolean> directAccessGrants = flag(keys, DIRECT_ACCESS_GRANTS);
        Optional<Boolean> serviceAccounts = flag(keys, SERVICE_ACCOUNTS);
        if (publicClient.isEmpty()
                || standardFlow.isEmpty()
                || implicitFlow.isEmpty()
                || directAccessGrants.isEmpty()
                || serviceAccounts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ClientFlows(
                publicClient.get(),
                standardFlow.get(),
                implicitFlow.get(),
                directAccessGrants.get(),
                serviceAccounts.get()));
    }

    private static Optional<Boolean> flag(final ElementKeys keys, final KeyPath key) {
        Optional<KeyValue> value = keys.get(key);
        if (value.isEmpty() || !(value.get() instanceof KeyValue.Scalar scalar)) {
            return Optional.empty();
        }
        return switch (scalar.value().raw()) {
            case "true" -> Optional.of(true);
            case "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }
}
