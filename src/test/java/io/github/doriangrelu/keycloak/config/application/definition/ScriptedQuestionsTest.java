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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.InvalidTemplatedValueException;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScriptedQuestionsTest {

    private static final List<Choice<String>> TYPES =
            List.of(new Choice<>("spa", "SPA", "browser"), new Choice<>("web", "Web", "server"));

    @Test
    void answersAreTakenInOrderAndQuestionsRecorded() {
        ScriptedQuestions questions = new ScriptedQuestions("frontend", "2", "y", "-", "a, b");

        assertThat(questions.ask("Client id", Optional.empty())).isEqualTo("frontend");
        assertThat(questions.choose("Type", TYPES, Optional.empty())).isEqualTo("web");
        assertThat(questions.confirm("Go?", false)).isTrue();
        assertThat(questions.askOptionalValue("Root URL", Optional.of(TemplatedValue.of("x"))))
                .isEmpty();
        assertThat(questions.askValues("URIs", List.of()))
                .containsExactly(TemplatedValue.of("a"), TemplatedValue.of("b"));
        assertThat(questions.asked()).containsExactly("Client id", "Type", "Go?", "Root URL", "URIs");
    }

    @Test
    void anEmptyAnswerTakesTheProposal() {
        ScriptedQuestions questions = new ScriptedQuestions("", "");

        assertThat(questions.choose("Type", TYPES, Optional.of("spa"))).isEqualTo("spa");
        assertThat(questions.askValue("Realm", Optional.of(TemplatedValue.of("r"))))
                .isEqualTo(TemplatedValue.of("r"));
    }

    @Test
    void anInvalidAnswerThrowsAndTheNextQuestionTakesTheNextAnswer() {
        ScriptedQuestions questions = new ScriptedQuestions("{{bad", "good");

        assertThatThrownBy(() -> questions.askValue("Realm", Optional.empty()))
                .isInstanceOf(InvalidTemplatedValueException.class);
        assertThat(questions.askValue("Realm", Optional.empty())).isEqualTo(TemplatedValue.of("good"));
        assertThat(questions.asked()).containsExactly("Realm", "Realm");
    }

    @Test
    void withoutAnswerLeftTheQuestionFails() {
        assertThatThrownBy(() -> new ScriptedQuestions().confirm("Go?", true))
                .isInstanceOf(MissingAnswerException.class)
                .hasMessageContaining("Go?");
    }

    @Test
    void messagesAreRecordedWithoutConsumingAnAnswer() {
        ScriptedQuestions questions = new ScriptedQuestions("a");

        questions.tell("Careful");

        assertThat(questions.told()).containsExactly("Careful");
        assertThat(questions.ask("Q", Optional.empty())).isEqualTo("a");
    }
}
