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
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Test double for {@link Questions}: answers come from a script, in order, as the user would type them, and follow the
 * rules of the console. Nothing loops: an invalid answer throws what the console would print, and a use case that
 * asks again simply takes the next answer. When no answer is left, {@link MissingAnswerException} is thrown.
 */
public final class ScriptedQuestions implements Questions {

    private static final Set<String> YES = Set.of("y", "yes", "o", "oui");
    private static final Set<String> NO = Set.of("n", "no", "non");

    private final Deque<String> answers;
    private final List<String> asked = new ArrayList<>();

    public ScriptedQuestions(final String... answers) {
        this(Arrays.asList(answers));
    }

    public ScriptedQuestions(final List<String> answers) {
        this.answers = new ArrayDeque<>(answers);
    }

    /** The questions asked so far, in order. */
    public List<String> asked() {
        return List.copyOf(this.asked);
    }

    @Override
    public String ask(final String question, final Optional<String> proposed) {
        String answer = next(question);
        if (!answer.isEmpty()) {
            return answer;
        }
        return proposed.orElseThrow(() -> new IllegalArgumentException("An answer is required: " + question));
    }

    @Override
    public <T> T choose(final String question, final List<Choice<T>> choices, final Optional<T> proposed) {
        String answer = next(question);
        if (answer.isEmpty() && proposed.isPresent()) {
            return proposed.get();
        }
        try {
            int number = Integer.parseInt(answer);
            if (number >= 1 && number <= choices.size()) {
                return choices.get(number - 1).value();
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException("Answer with a number from 1 to " + choices.size() + ": " + question);
    }

    @Override
    public TemplatedValue askValue(final String question, final Optional<TemplatedValue> proposed) {
        return TemplatedValue.of(ask(question, proposed.map(TemplatedValue::raw)));
    }

    @Override
    public Optional<TemplatedValue> askOptionalValue(final String question, final Optional<TemplatedValue> proposed) {
        String answer = next(question);
        if (answer.isEmpty()) {
            return proposed;
        }
        if (answer.equals("-")) {
            return Optional.empty();
        }
        return Optional.of(TemplatedValue.of(answer));
    }

    @Override
    public List<TemplatedValue> askValues(final String question, final List<TemplatedValue> proposed) {
        String answer = next(question);
        if (answer.isEmpty()) {
            return proposed;
        }
        if (answer.equals("-")) {
            return List.of();
        }
        return Arrays.stream(answer.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(TemplatedValue::of)
                .toList();
    }

    @Override
    public boolean confirm(final String question, final boolean byDefault) {
        String answer = next(question).toLowerCase(Locale.ROOT);
        if (answer.isEmpty()) {
            return byDefault;
        }
        if (YES.contains(answer)) {
            return true;
        }
        if (NO.contains(answer)) {
            return false;
        }
        throw new IllegalArgumentException("Answer yes or no: " + question);
    }

    private String next(final String question) {
        this.asked.add(question);
        String answer = this.answers.poll();
        if (answer == null) {
            throw new MissingAnswerException(question);
        }
        return answer.strip();
    }
}
