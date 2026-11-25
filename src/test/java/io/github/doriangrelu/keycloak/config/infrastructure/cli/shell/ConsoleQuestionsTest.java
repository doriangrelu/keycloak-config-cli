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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.definition.Choice;
import io.github.doriangrelu.keycloak.config.application.definition.MissingAnswerException;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.shell.core.InputReader;

class ConsoleQuestionsTest {

    private final StringWriter output = new StringWriter();

    private ConsoleQuestions answering(final String... answers) {
        Deque<String> pending = new ArrayDeque<>(Arrays.asList(answers));
        InputReader reader = new InputReader() {
            @Override
            public String readInput() {
                return pending.poll();
            }

            @Override
            public String readInput(final String prompt) {
                return pending.poll();
            }
        };
        return new ConsoleQuestions(reader, new PrintWriter(this.output, true));
    }

    private static final List<Choice<String>> TYPES = List.of(
            new Choice<>("spa", "Front-end application (SPA)", "runs in the browser, cannot keep a secret"),
            new Choice<>("web", "Web application with a server", "the secret stays on the server"),
            new Choice<>("service", "Service to service", "calls other services with its own rights"));

    @Test
    void anEmptyAnswerTakesTheProposedValue() {
        assertThat(answering("").ask("Client id", Optional.of("frontend"))).isEqualTo("frontend");
    }

    @Test
    void anEmptyAnswerWithoutProposalIsAskedAgain() {
        assertThat(answering("", "frontend").ask("Client id", Optional.empty())).isEqualTo("frontend");
    }

    @Test
    void aChoiceIsAnsweredByItsNumber() {
        assertThat(answering("2").choose("Application type", TYPES, Optional.empty()))
                .isEqualTo("web");
        assertThat(this.output.toString())
                .contains("  2. Web application with a server — the secret stays on the server");
    }

    @Test
    void anInvalidChoiceIsAskedAgain() {
        assertThat(answering("7", "x", "1").choose("Application type", TYPES, Optional.empty()))
                .isEqualTo("spa");
        assertThat(this.output.toString().lines().filter(line -> line.contains("Answer with a number")))
                .hasSize(2);
    }

    @Test
    void anEmptyChoiceTakesTheProposedValue() {
        assertThat(answering("").choose("Application type", TYPES, Optional.of("service")))
                .isEqualTo("service");
    }

    @Test
    void confirmUnderstandsEnglishAndFrench() {
        assertThat(answering("oui").confirm("Continue?", false)).isTrue();
        assertThat(answering("n").confirm("Continue?", true)).isFalse();
        assertThat(answering("").confirm("Continue?", false)).isFalse();
        assertThat(answering("maybe", "yes").confirm("Continue?", false)).isTrue();
    }

    @Test
    void aMissingAnswerFailsInsteadOfWaiting() {
        assertThatThrownBy(() -> answering().ask("Client id", Optional.empty()))
                .isInstanceOf(MissingAnswerException.class)
                .hasMessageContaining("Client id");
    }

    @Test
    void withoutAnswerAProposalIsTaken() {
        ConsoleQuestions script = answering();

        assertThat(script.ask("Client id", Optional.of("frontend"))).isEqualTo("frontend");
        assertThat(script.askValue("Application URL", Optional.of(TemplatedValue.of("{{appUrl}}"))))
                .isEqualTo(TemplatedValue.of("{{appUrl}}"));
        assertThat(script.askOptionalValue("Name", Optional.of(TemplatedValue.of("Shop"))))
                .contains(TemplatedValue.of("Shop"));
        assertThat(script.askValues("Redirect URIs", List.of(TemplatedValue.of("a"))))
                .containsExactly(TemplatedValue.of("a"));
        assertThat(script.choose("Application type", TYPES, Optional.of("service")))
                .isEqualTo("service");
        assertThat(script.confirm("Continue?", false)).isFalse();
        assertThat(script.confirm("Continue?", true)).isTrue();
    }

    @Test
    void withoutAnswerNothingIsGivenWhenNothingIsProposed() {
        ConsoleQuestions script = answering();

        assertThat(script.askOptionalValue("Name", Optional.empty())).isEmpty();
        assertThat(script.askValues("Web origins", List.of())).isEmpty();
    }

    @Test
    void withoutAnswerAndWithoutProposalTheQuestionFails() {
        assertThatThrownBy(() -> answering().ask("Client id", Optional.empty()))
                .isInstanceOf(MissingAnswerException.class);
        assertThatThrownBy(() -> answering().askValue("Application URL", Optional.empty()))
                .isInstanceOf(MissingAnswerException.class);
        assertThatThrownBy(() -> answering().choose("Application type", TYPES, Optional.empty()))
                .isInstanceOf(MissingAnswerException.class)
                .hasMessageContaining("Application type");
    }

    @Test
    void anOptionalValueSaysHowToKeepAndClearIt() {
        answering("").askOptionalValue("Name", Optional.of(TemplatedValue.of("Shop")));
        answering("").askOptionalValue("Description", Optional.empty());

        assertThat(this.output.toString())
                .contains("? Name [Shop] (Enter keeps it, - for none): ")
                .contains("? Description (Enter for none): ");
    }

    @Test
    void aListSaysHowToKeepAndClearIt() {
        answering("").askValues("Redirect URIs", List.of(TemplatedValue.of("{{appUrl}}/*")));
        answering("").askValues("Web origins", List.of());

        assertThat(this.output.toString())
                .contains("? Redirect URIs [{{appUrl}}/*] (Enter keeps them, - for none): ")
                .contains("? Web origins (Enter for none): ");
    }

    @Test
    void aValueMayHoldVariables() {
        assertThat(answering("{{env}}-mon-realm").askValue("Realm name", Optional.empty()))
                .isEqualTo(TemplatedValue.of("{{env}}-mon-realm"));
    }

    @Test
    void anInvalidPlaceholderIsAskedAgain() {
        assertThat(answering("{{env-mon-realm", "{{env}}-mon-realm").askValue("Realm name", Optional.empty()))
                .isEqualTo(TemplatedValue.of("{{env}}-mon-realm"));
        assertThat(this.output.toString()).contains("Invalid value '{{env-mon-realm'");
    }

    @Test
    void anEmptyValueTakesTheProposedOne() {
        assertThat(answering("").askValue("Application URL", Optional.of(TemplatedValue.of("{{appUrl}}"))))
                .isEqualTo(TemplatedValue.of("{{appUrl}}"));
    }

    @Test
    void aMissingValueFailsInsteadOfWaiting() {
        assertThatThrownBy(() -> answering().askValue("Realm name", Optional.empty()))
                .isInstanceOf(MissingAnswerException.class)
                .hasMessageContaining("Realm name");
    }

    @Test
    void anEmptyOptionalValueTakesTheProposedOne() {
        assertThat(answering("").askOptionalValue("Root URL", Optional.of(TemplatedValue.of("{{appUrl}}"))))
                .contains(TemplatedValue.of("{{appUrl}}"));
    }

    @Test
    void aDashClearsAnOptionalValue() {
        assertThat(answering("-").askOptionalValue("Root URL", Optional.of(TemplatedValue.of("{{appUrl}}"))))
                .isEmpty();
    }

    @Test
    void anOptionalValueWithoutProposalMayBeEmpty() {
        assertThat(answering("").askOptionalValue("Root URL", Optional.empty())).isEmpty();
        assertThat(this.output.toString()).doesNotContain("An answer is required");
        assertThat(answering("https://a").askOptionalValue("Root URL", Optional.empty()))
                .contains(TemplatedValue.of("https://a"));
    }

    @Test
    void valuesAreSplitOnCommas() {
        assertThat(answering("{{appUrl}}/* , https://a/*").askValues("Redirect URIs", List.of()))
                .containsExactly(TemplatedValue.of("{{appUrl}}/*"), TemplatedValue.of("https://a/*"));
        assertThat(answering("a,,b,").askValues("Redirect URIs", List.of()))
                .containsExactly(TemplatedValue.of("a"), TemplatedValue.of("b"));
    }

    @Test
    void anEmptyListAnswerTakesTheProposal() {
        List<TemplatedValue> proposed = List.of(TemplatedValue.of("a"), TemplatedValue.of("b"));
        assertThat(answering("").askValues("Redirect URIs", proposed)).isEqualTo(proposed);
        assertThat(this.output.toString()).contains("? Redirect URIs [a, b] (Enter keeps them, - for none): ");
    }

    @Test
    void aDashGivesNoValue() {
        assertThat(answering("-").askValues("Redirect URIs", List.of(TemplatedValue.of("a"))))
                .isEmpty();
    }

    @Test
    void anInvalidValueInAListIsAskedAgain() {
        assertThat(answering("a,{{env", "a,b").askValues("Redirect URIs", List.of()))
                .containsExactly(TemplatedValue.of("a"), TemplatedValue.of("b"));
        assertThat(this.output.toString()).contains("Invalid value '{{env'");
    }

    @Test
    void aMessageIsPrintedIndented() {
        answering().tell("Client 'x' already exists");

        assertThat(this.output.toString()).contains("  Client 'x' already exists");
    }
}
