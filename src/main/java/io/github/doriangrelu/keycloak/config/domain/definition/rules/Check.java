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

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A test on the value of a key of an element (ADR-009). The pattern of matches and none-matches is compiled when the
 * check is built.
 *
 * @param key the key tested
 * @param operator the operator
 * @param operands one for equals, matches and none-matches; a list for one-of and not-one-of; none for present and
 *     absent
 */
public record Check(KeyPath key, Operator operator, List<String> operands) {

    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    public Check {
        operands = List.copyOf(operands);
        validate(operator, operands);
        pattern(operator, operands);
    }

    private static void validate(final Operator operator, final List<String> operands) {
        boolean valid = switch (operator) {
            case EQUALS, MATCHES, NONE_MATCHES -> operands.size() == 1;
            case ONE_OF, NOT_ONE_OF -> !operands.isEmpty();
            case PRESENT, ABSENT -> operands.isEmpty();
        };
        if (!valid) {
            throw new InvalidRulesException(
                    "The operator '" + operator + "' does not accept " + operands.size() + " operand(s)");
        }
    }

    /** The compiled pattern of matches and none-matches (compiled once, when the check is built). */
    private static Optional<Pattern> pattern(final Operator operator, final List<String> operands) {
        if (operator != Operator.MATCHES && operator != Operator.NONE_MATCHES) {
            return Optional.empty();
        }
        String regex = operands.get(0);
        try {
            return Optional.of(PATTERNS.computeIfAbsent(regex, Pattern::compile));
        } catch (PatternSyntaxException e) {
            throw new InvalidRulesException(
                    "The pattern '" + regex + "' is not a valid regular expression: " + e.getDescription());
        }
    }

    /** Tests the keys of an element. */
    public Outcome evaluate(final ElementKeys keys) {
        Optional<KeyValue> found = keys.get(this.key);
        if (this.operator == Operator.PRESENT) {
            return found.isPresent() ? Outcome.PASSED : Outcome.FAILED;
        }
        if (this.operator == Operator.ABSENT) {
            return found.isPresent() ? Outcome.FAILED : Outcome.PASSED;
        }
        if (found.isEmpty()) {
            return absentOutcome();
        }
        return switch (found.get()) {
            case KeyValue.Shapeless shapeless -> Outcome.UNVERIFIABLE;
            case KeyValue.Scalar scalar ->
                this.operator == Operator.EQUALS ? equalsOutcome(scalar.value()) : items(List.of(scalar.value()));
            case KeyValue.Listed listed ->
                this.operator == Operator.EQUALS ? Outcome.UNVERIFIABLE : items(listed.values());
        };
    }

    private Outcome absentOutcome() {
        return this.operator == Operator.NOT_ONE_OF || this.operator == Operator.NONE_MATCHES
                ? Outcome.PASSED
                : Outcome.FAILED;
    }

    private Outcome equalsOutcome(final TemplatedValue value) {
        if (value.isTemplated()) {
            return Outcome.UNVERIFIABLE;
        }
        return value.raw().equals(this.operands.get(0)) ? Outcome.PASSED : Outcome.FAILED;
    }

    /** A literal item that breaks the check decides FAILED; otherwise a templated item makes it unverifiable. */
    private Outcome items(final List<TemplatedValue> values) {
        boolean templated = false;
        for (TemplatedValue value : values) {
            if (value.isTemplated()) {
                templated = true;
            } else if (!itemPasses(value.raw())) {
                return Outcome.FAILED;
            }
        }
        return templated ? Outcome.UNVERIFIABLE : Outcome.PASSED;
    }

    private boolean itemPasses(final String text) {
        return switch (this.operator) {
            case ONE_OF -> this.operands.contains(text);
            case NOT_ONE_OF -> !this.operands.contains(text);
            case MATCHES ->
                pattern(this.operator, this.operands)
                        .orElseThrow()
                        .matcher(text)
                        .matches();
            case NONE_MATCHES ->
                !pattern(this.operator, this.operands)
                        .orElseThrow()
                        .matcher(text)
                        .matches();
            default -> throw new IllegalStateException("Not an item operator: " + this.operator);
        };
    }
}
