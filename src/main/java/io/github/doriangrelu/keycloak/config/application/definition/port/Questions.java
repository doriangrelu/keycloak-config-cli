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

package io.github.doriangrelu.keycloak.config.application.definition.port;

import io.github.doriangrelu.keycloak.config.application.definition.Choice;
import io.github.doriangrelu.keycloak.config.application.definition.MissingAnswerException;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Optional;

/**
 * The questions a guided command asks (ADR-008). The use cases decide which questions to ask and what to propose; an
 * input adapter, such as the shell, asks them.
 *
 * <p>Every method throws {@link MissingAnswerException} when no answer can be given, such as in a script.
 */
public interface Questions {

    /** A free answer; an empty answer takes {@code proposed}, and is asked again when nothing is proposed. */
    String ask(String question, Optional<String> proposed);

    /** One of the choices; an empty answer takes {@code proposed}, and is asked again when nothing is proposed. */
    <T> T choose(String question, List<Choice<T>> choices, Optional<T> proposed);

    /**
     * Several of the choices, answered by their numbers separated by commas; the values come back in the order of the
     * list, whatever the order of the answer. An empty answer takes {@code proposed}; a single {@code -} gives no
     * value. A number outside the list is asked again. Without choices, nothing is asked and nothing is chosen.
     */
    <T> List<T> chooseMany(String question, List<Choice<T>> choices, List<T> proposed);

    /**
     * A value written in a definition file: plain, a variable ({@code {{appUrl}}}, {@code {{appUrl:default}}}) or a
     * value composed with variables ({@code {{env}}-my-realm}). A value whose placeholder is invalid is asked again;
     * an empty answer takes {@code proposed}.
     */
    TemplatedValue askValue(String question, Optional<TemplatedValue> proposed);

    /**
     * An optional value, written like {@link #askValue}. An empty answer takes {@code proposed}, which may be empty
     * too; a single {@code -} gives no value.
     */
    Optional<TemplatedValue> askOptionalValue(String question, Optional<TemplatedValue> proposed);

    /**
     * Values separated by commas, each written like {@link #askValue}. An empty answer takes {@code proposed}; a
     * single {@code -} gives no value. A list whose value is invalid is asked again.
     */
    List<TemplatedValue> askValues(String question, List<TemplatedValue> proposed);

    /** Yes or no; an empty answer takes {@code byDefault}. */
    boolean confirm(String question, boolean byDefault);

    /** A message to the user, such as why the last answer is asked again; it expects no answer. */
    void tell(String message);
}
