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

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Objects;
import java.util.Optional;

/**
 * The answers already given to the questions of a role, such as by the options of a command: a given answer is not
 * asked.
 *
 * @param client the client id, as written, of a client role; empty when not given, which asks the owner
 * @param declareType whether writing the first role of its place is already confirmed
 */
public record RoleAnswers(
        Optional<TemplatedValue> client,
        Optional<TemplatedValue> name,
        Optional<TemplatedValue> description,
        boolean declareType) {

    public RoleAnswers {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }

    /** Nothing is given: every question is asked. */
    public static RoleAnswers none() {
        return new RoleAnswers(Optional.empty(), Optional.empty(), Optional.empty(), false);
    }
}
