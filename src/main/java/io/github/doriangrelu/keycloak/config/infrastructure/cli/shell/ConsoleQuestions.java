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
import java.io.Console;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.shell.core.InputReader;

/**
 * Asks the questions on the console: free answers, numbered choices with their explanation, yes or no. An invalid
 * answer is asked again. No answer at all (a script) counts as an empty answer: the proposal is taken, a confirmation
 * takes its default, and a question with nothing to take fails with {@link MissingAnswerException}.
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
            String answer = read(prompt);
            if (answer == null) {
                return proposed.orElseThrow(() -> new MissingAnswerException(question));
            }
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
        String prompt = optionalPromptOf(question, proposed.map(TemplatedValue::raw), "it");
        while (true) {
            String answer = read(prompt);
            if (answer == null || answer.isEmpty()) {
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
        String prompt = optionalPromptOf(question, proposed.isEmpty() ? Optional.empty() : Optional.of(shown), "them");
        while (true) {
            String answer = read(prompt);
            if (answer == null || answer.isEmpty()) {
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
            String answer = read("  Number: ");
            if (answer == null) {
                return proposed.orElseThrow(() -> new MissingAnswerException(question));
            }
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
    public <T> List<T> chooseMany(final String question, final List<Choice<T>> choices, final List<T> proposed) {
        if (choices.isEmpty()) {
            return List.of();
        }
        this.writer.println("? " + question);
        List<Integer> proposedNumbers = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            Choice<T> choice = choices.get(i);
            boolean current = proposed.contains(choice.value());
            if (current) {
                proposedNumbers.add(i + 1);
            }
            this.writer.println("  " + (i + 1) + ". " + choice.label() + " — " + choice.explanation()
                    + (current ? " (current)" : ""));
        }
        this.writer.flush();
        String shown = proposedNumbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String prompt = "  Numbers"
                + (proposedNumbers.isEmpty()
                        ? " (Enter for none)"
                        : " [" + shown + "] (Enter keeps them, " + NONE + " for none)")
                + ": ";
        while (true) {
            String answer = read(prompt);
            if (answer == null || answer.isEmpty()) {
                return proposed;
            }
            if (answer.equals(NONE)) {
                return List.of();
            }
            Optional<Set<Integer>> numbers = numbersOf(answer, choices.size());
            if (numbers.isPresent()) {
                List<T> chosen = new ArrayList<>();
                for (int i = 1; i <= choices.size(); i++) {
                    if (numbers.get().contains(i)) {
                        chosen.add(choices.get(i - 1).value());
                    }
                }
                return chosen;
            }
            this.writer.println("  Answer with numbers from 1 to " + choices.size() + ", separated by commas.");
        }
    }

    @Override
    public String askSecret(final String question) {
        String prompt = "? " + question + ": ";
        while (true) {
            String answer = readSecret(prompt);
            if (answer == null) {
                throw new MissingAnswerException(question);
            }
            if (!answer.isEmpty()) {
                return answer;
            }
            this.writer.println("  An answer is required.");
        }
    }

    @Override
    public boolean confirm(final String question, final boolean byDefault) {
        String prompt = "? " + question + (byDefault ? " [Y/n]" : " [y/N]") + ": ";
        while (true) {
            String answer = read(prompt);
            if (answer == null) {
                return byDefault;
            }
            answer = answer.toLowerCase(Locale.ROOT);
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

    @Override
    public void tell(final String message) {
        this.writer.println("  " + message);
        this.writer.flush();
    }

    private static String promptOf(final String question, final Optional<String> proposed) {
        return "? " + question + proposed.map(value -> " [" + value + "]").orElse("") + ": ";
    }

    /** The prompt of an optional answer: it says how to keep the proposal and how to give nothing. */
    private static String optionalPromptOf(final String question, final Optional<String> proposed, final String keeps) {
        String hint =
                proposed.isPresent() ? " (Enter keeps " + keeps + ", " + NONE + " for none)" : " (Enter for none)";
        return "? " + question + proposed.map(value -> " [" + value + "]").orElse("") + hint + ": ";
    }

    /**
     * The secret typed without echo, or null when there is none to read, as in a script. The reader hides the input
     * when it can; otherwise the system console does; as a last resort (no terminal at all, such as a pipe) the input is
     * read in clear, after a warning that says so: the question is then still answered rather than lost.
     */
    private String readSecret(final String prompt) {
        try {
            // the reader of the console takes the prompt for a format: a "%" of a user or a folder name is escaped
            return asText(this.reader.readPassword(prompt.replace("%", "%%")));
        } catch (UnsupportedOperationException cannotHide) {
            // the reader has no hidden input: the console or, failing that, a plain read
        } catch (Exception e) {
            return null;
        }
        Console console = System.console();
        if (console != null && console.isTerminal()) {
            return asText(console.readPassword("%s", prompt));
        }
        this.writer.println("  Warning: this input cannot be hidden, what you type is echoed.");
        return read(prompt);
    }

    /** The text of the characters, which are then erased; null when nothing could be read. */
    private static String asText(final char[] typed) {
        if (typed == null) {
            return null;
        }
        String text = new String(typed);
        Arrays.fill(typed, '\0');
        return text;
    }

    /** The answer, or null when there is none to read: a script does not answer, and the question takes its proposal. */
    private String read(final String prompt) {
        this.writer.print(prompt);
        this.writer.flush();
        String answer;
        try {
            answer = this.reader.readInput(prompt);
        } catch (Exception e) {
            return null;
        }
        return answer == null ? null : answer.trim();
    }

    /** The numbers of a comma separated answer, none outside 1 to {@code max}; empty when the answer is invalid. */
    private static Optional<Set<Integer>> numbersOf(final String answer, final int max) {
        Set<Integer> numbers = new HashSet<>();
        for (String part : answer.split(",")) {
            String text = part.strip();
            if (text.isEmpty()) {
                continue;
            }
            Optional<Integer> number = numberOf(text).filter(n -> n >= 1 && n <= max);
            if (number.isEmpty()) {
                return Optional.empty();
            }
            numbers.add(number.get());
        }
        return numbers.isEmpty() ? Optional.empty() : Optional.of(numbers);
    }

    private static Optional<Integer> numberOf(final String answer) {
        try {
            return Optional.of(Integer.parseInt(answer));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
