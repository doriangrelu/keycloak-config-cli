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

import io.github.doriangrelu.keycloak.config.application.definition.Choice;
import io.github.doriangrelu.keycloak.config.application.definition.MissingAnswerException;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.InvalidTemplatedValueException;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.shell.core.InputReader;

/**
 * Asks the questions on the console: free answers, numbered choices with their explanation, yes or no. An invalid
 * answer is asked again; no answer at all (end of input, as in a script) fails with {@link MissingAnswerException}.
 */
public final class ConsoleQuestions implements Questions {

    private static final Set<String> YES = Set.of("y", "yes", "o", "oui");
    private static final Set<String> NO = Set.of("n", "no", "non");

    /** The answer that gives no value to an optional question or to a list. */
    private static final String NONE = "-";

    private final InputReader reader;
    private final PrintWriter writer;

    public ConsoleQuestions(final InputReader reader, final PrintWriter writer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public String ask(final String question, final Optional<String> proposed) {
        String prompt = promptOf(question, proposed);
        while (true) {
            String answer = read(question, prompt);
            if (!answer.isEmpty()) {
                return answer;
            }
            if (proposed.isPresent()) {
                return proposed.get();
            }
            this.writer.println("  An answer is required.");
        }
    }

    @Override
    public TemplatedValue askValue(final String question, final Optional<TemplatedValue> proposed) {
        while (true) {
            String answer = ask(question, proposed.map(TemplatedValue::raw));
            try {
                return TemplatedValue.of(answer);
            } catch (InvalidTemplatedValueException e) {
                this.writer.println("  " + e.getMessage());
            }
        }
    }

    @Override
    public Optional<TemplatedValue> askOptionalValue(final String question, final Optional<TemplatedValue> proposed) {
        String prompt = promptOf(question, proposed.map(TemplatedValue::raw));
        while (true) {
            String answer = read(question, prompt);
            if (answer.isEmpty()) {
                return proposed;
            }
            if (answer.equals(NONE)) {
                return Optional.empty();
            }
            try {
                return Optional.of(TemplatedValue.of(answer));
            } catch (InvalidTemplatedValueException e) {
                this.writer.println("  " + e.getMessage());
            }
        }
    }

    @Override
    public List<TemplatedValue> askValues(final String question, final List<TemplatedValue> proposed) {
        String shown = proposed.stream().map(TemplatedValue::raw).collect(Collectors.joining(", "));
        String prompt = promptOf(question, proposed.isEmpty() ? Optional.empty() : Optional.of(shown));
        while (true) {
            String answer = read(question, prompt);
            if (answer.isEmpty()) {
                return proposed;
            }
            if (answer.equals(NONE)) {
                return List.of();
            }
            try {
                return Arrays.stream(answer.split(","))
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .map(TemplatedValue::of)
                        .toList();
            } catch (InvalidTemplatedValueException e) {
                this.writer.println("  " + e.getMessage());
            }
        }
    }

    @Override
    public <T> T choose(final String question, final List<Choice<T>> choices, final Optional<T> proposed) {
        this.writer.println("? " + question);
        for (int i = 0; i < choices.size(); i++) {
            Choice<T> choice = choices.get(i);
            String marker = proposed.filter(choice.value()::equals).isPresent() ? " (current)" : "";
            this.writer.println("  " + (i + 1) + ". " + choice.label() + " — " + choice.explanation() + marker);
        }
        this.writer.flush();
        while (true) {
            String answer = read(question, "  Number: ");
            if (answer.isEmpty() && proposed.isPresent()) {
                return proposed.get();
            }
            Optional<Integer> number = numberOf(answer).filter(n -> n >= 1 && n <= choices.size());
            if (number.isPresent()) {
                return choices.get(number.get() - 1).value();
            }
            this.writer.println("  Answer with a number from 1 to " + choices.size() + ".");
        }
    }

    @Override
    public boolean confirm(final String question, final boolean byDefault) {
        String prompt = "? " + question + (byDefault ? " [Y/n]" : " [y/N]") + ": ";
        while (true) {
            String answer = read(question, prompt).toLowerCase(Locale.ROOT);
            if (answer.isEmpty()) {
                return byDefault;
            }
            if (YES.contains(answer)) {
                return true;
            }
            if (NO.contains(answer)) {
                return false;
            }
            this.writer.println("  Answer yes or no.");
        }
    }

    private static String promptOf(final String question, final Optional<String> proposed) {
        return "? " + question + proposed.map(value -> " [" + value + "]").orElse("") + ": ";
    }

    private String read(final String question, final String prompt) {
        this.writer.print(prompt);
        this.writer.flush();
        String answer;
        try {
            answer = this.reader.readInput(prompt);
        } catch (Exception e) {
            throw new MissingAnswerException(question);
        }
        if (answer == null) {
            throw new MissingAnswerException(question);
        }
        return answer.trim();
    }

    private static Optional<Integer> numberOf(final String answer) {
        try {
            return Optional.of(Integer.parseInt(answer));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
