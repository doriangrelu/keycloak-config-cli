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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The answers already given to the questions of a group, such as by the options of a command: a given answer is not
 * asked.
 *
 * @param parent the path of the parent group; empty when not given, which asks it
 * @param realmRoles the realm roles to give; empty when not given
 * @param clientRoles the client roles to give, as {@code client/role}; empty when not given
 * @param declareType whether writing the first group of the realm is already confirmed
 */
public record GroupAnswers(
        Optional<TemplatedValue> name,
        Optional<String> parent,
        Optional<List<String>> realmRoles,
        Optional<List<String>> clientRoles,
        boolean declareType) {

    public GroupAnswers {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(realmRoles, "realmRoles");
        Objects.requireNonNull(clientRoles, "clientRoles");
    }

    /** Nothing is given: every question is asked. */
    public static GroupAnswers none() {
        return new GroupAnswers(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
    }
}
