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

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** A question got no answer: in a script, every answer is given as an option of the command (ADR-008). */
public final class MissingAnswerException extends ApplicationException {

    public MissingAnswerException(final String question) {
        this(question, "give it as an option of the command");
    }

    /** A question whose answer is given another way than an option, such as an environment variable. */
    public MissingAnswerException(final String question, final String how) {
        super("No answer to \"" + question + "\": " + how);
    }
}
