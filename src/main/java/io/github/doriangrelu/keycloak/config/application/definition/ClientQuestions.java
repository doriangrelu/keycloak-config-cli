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
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Optional;

/** The questions a client is asked, whether it is created or edited. */
final class ClientQuestions {

    static final List<Choice<ApplicationType>> TYPES = List.of(
            new Choice<>(
                    ApplicationType.SPA, "Front-end application (SPA)", "runs in the browser, cannot keep a secret"),
            new Choice<>(
                    ApplicationType.WEB_APPLICATION, "Web application with a server", "the secret stays on the server"),
            new Choice<>(
                    ApplicationType.SERVICE, "Service to service", "calls other services with its own rights, no user"),
            new Choice<>(ApplicationType.API, "API", "receives tokens, logs nobody in"));

    private static final List<Choice<Boolean>> SECRETS = List.of(
            new Choice<>(
                    true, "A variable (recommended)", "the secret is given at import, the same everywhere you give it"),
            new Choice<>(false, "None, Keycloak generates it", "read it in the Keycloak console; the import keeps it"));

    private ClientQuestions() {}

    /**
     * Asks whether the secret is a variable, then which one.
     *
     * @param current the secret written today, if any
     * @param proposedChoice what the first question proposes: a variable, or none
     */
    static SecretAnswer askSecret(
            final TemplatedValue clientId,
            final Optional<TemplatedValue> current,
            final Optional<Boolean> proposedChoice,
            final Questions questions) {
        if (!questions.choose("Client secret", SECRETS, proposedChoice)) {
            return new SecretAnswer.None();
        }
        Optional<TemplatedValue> proposed = current.filter(TemplatedValue::isFullyVariable)
                .or(() -> Optional.of(SecretVariables.proposedFor(clientId)));
        while (true) {
            TemplatedValue answer = questions.askValue("Secret variable", proposed);
            if (answer.isFullyVariable()) {
                return new SecretAnswer.Variable(answer);
            }
            questions.tell(HardCodedSecretException.MESSAGE);
        }
    }
}
