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

import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.VariableUse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Finds the value of each variable of a realm for an import. A variable takes, in this order: the value given with
 * the command, the value the session remembers, the environment variable {@code KCC_VAR_<name>}, the default value
 * of its placeholder, then the answer to a question.
 */
public final class ResolveVariables {

    private static final String ENVIRONMENT_PREFIX = "KCC_VAR_";

    private final ListVariables variables;
    private final Function<String, Optional<String>> environment;
    private final Questions questions;

    public ResolveVariables(
            final ListVariables variables,
            final Function<String, Optional<String>> environment,
            final Questions questions) {
        this.variables = Objects.requireNonNull(variables, "variables");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.questions = Objects.requireNonNull(questions, "questions");
    }

    /**
     * Reads the {@code --var name=value} options.
     *
     * @throws InvalidVariableOptionException when an option has no name or no equal sign
     */
    public static Map<String, String> parseVars(final List<String> options) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String option : options) {
            int equal = option.indexOf('=');
            String name = equal < 0 ? option : option.substring(0, equal);
            if (equal < 0 || name.isBlank()) {
                throw new InvalidVariableOptionException();
            }
            values.put(name, option.substring(equal + 1));
        }
        return values;
    }

    /**
     * @param given the values given with the command
     * @param remembered the values of the session; the given values and the answers are added to it
     * @return the value of each variable that has one besides its default value, which rendering applies
     * @throws MissingVariableException when a variable has no value and no answer can be asked
     * @throws UnknownRealmFolderException when the folder is not a realm
     */
    public Map<String, String> resolve(
            final String folder, final Map<String, String> given, final VariableValues remembered) {
        List<VariableUse> uses = this.variables.list(folder);
        given.forEach(remembered::remember);
        Map<String, String> values = new LinkedHashMap<>();
        for (VariableUse use : uses) {
            valueOf(use, given, remembered).ifPresent(value -> values.put(use.name(), value));
        }
        return values;
    }

    /**
     * The value of each variable that has one, from the session and the environment only: nothing is asked, nothing
     * is remembered.
     *
     * @throws MissingVariableException for the first variable that has no value and no default value
     * @throws UnknownRealmFolderException when the folder is not a realm
     */
    public Map<String, String> resolveKnown(final String folder, final VariableValues remembered) {
        Map<String, String> values = new LinkedHashMap<>();
        for (VariableUse use : this.variables.list(folder)) {
            String name = use.name();
            Optional<String> value = remembered.get(name).or(() -> this.environment.apply(ENVIRONMENT_PREFIX + name));
            if (value.isEmpty() && use.usedWithoutDefault()) {
                throw new MissingVariableException(name);
            }
            value.ifPresent(known -> values.put(name, known));
        }
        return values;
    }

    private Optional<String> valueOf(
            final VariableUse use, final Map<String, String> given, final VariableValues remembered) {
        String name = use.name();
        Optional<String> value = Optional.ofNullable(given.get(name))
                .or(() -> remembered.get(name))
                .or(() -> this.environment.apply(ENVIRONMENT_PREFIX + name));
        // a variable with a default value in every placeholder needs none; one placeholder without a default is
        // enough to need a value, which then applies to every placeholder
        if (value.isPresent() || !use.usedWithoutDefault()) {
            return value;
        }
        String answer = answer(name);
        remembered.remember(name, answer);
        return Optional.of(answer);
    }

    private String answer(final String name) {
        String question = "Value of '" + name + "'";
        try {
            return isSecret(name) ? this.questions.askSecret(question) : this.questions.ask(question, Optional.empty());
        } catch (MissingAnswerException e) {
            throw new MissingVariableException(name);
        }
    }

    private static boolean isSecret(final String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("secret") || lower.contains("password");
    }
}
