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

import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Disablement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Finding;
import java.util.List;
import java.util.Map;

/**
 * What the rules say about a realm as it is.
 *
 * @param disabled the rules the organisation switched off, with the reason
 * @param findings the non-conformities of the elements, by origin; only the elements that have some
 * @param realmFindings the non-conformities of the realm itself
 */
public record RealmCheck(List<Disablement> disabled, Map<Origin, List<Finding>> findings, List<Finding> realmFindings) {

    public RealmCheck {
        disabled = List.copyOf(disabled);
        findings = Map.copyOf(findings);
        realmFindings = List.copyOf(realmFindings);
    }
}
