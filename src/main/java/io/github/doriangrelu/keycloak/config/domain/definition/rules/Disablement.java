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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import java.util.Optional;
import java.util.Set;

/**
 * The switching off of a rule by the organisation, with the reason why.
 *
 * @param id the identifier of the rule
 * @param forElements the elements it is switched off for; empty for all of them
 * @param reason why the rule is switched off
 */
public record Disablement(String id, Set<String> forElements, String reason) {

    public Disablement {
        if (id == null || id.isBlank()) {
            throw new InvalidRulesException("A disablement needs a rule identifier");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidRulesException("The disablement of the rule '" + id + "' needs a reason");
        }
        forElements = Set.copyOf(forElements);
    }

    /** Whether the rule is switched off for this element. */
    boolean covers(final Optional<String> elementId) {
        return this.forElements.isEmpty()
                || elementId.filter(this.forElements::contains).isPresent();
    }
}
