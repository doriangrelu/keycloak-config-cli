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

package io.github.doriangrelu.keycloak.config.application.importation;

import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an import changed, by realm in import order: applied, or only planned in a dry run.
 *
 * @param plans the changes of each realm
 */
public record ImportReport(Map<String, List<PlannedChange>> plans) {

    public ImportReport {
        Map<String, List<PlannedChange>> copy = new LinkedHashMap<>();
        plans.forEach((realm, changes) -> copy.put(realm, List.copyOf(changes)));
        plans = java.util.Collections.unmodifiableMap(copy);
    }

    /** The changes of one realm; none when the import did not touch it. */
    public List<PlannedChange> plan(final String realm) {
        return this.plans.getOrDefault(realm, List.of());
    }
}
