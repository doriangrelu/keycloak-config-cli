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

import io.github.doriangrelu.keycloak.config.application.reconcile.NotComparedValue;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an import changed, by realm in import order: applied, or only planned in a dry run.
 *
 * @param plans the changes of each realm
 * @param notCompared by realm, the values the files declare that Keycloak hides, which were not compared: they are no
 *     change
 */
public record ImportReport(Map<String, List<PlannedChange>> plans, Map<String, List<NotComparedValue>> notCompared) {

    public ImportReport {
        plans = copyOf(plans);
        notCompared = copyOf(notCompared);
    }

    /** A report where every value was compared. */
    public ImportReport(final Map<String, List<PlannedChange>> plans) {
        this(plans, Map.of());
    }

    /** The changes of one realm; none when the import did not touch it. */
    public List<PlannedChange> plan(final String realm) {
        return this.plans.getOrDefault(realm, List.of());
    }

    /** The values of one realm that were not compared because Keycloak hides them. */
    public List<NotComparedValue> notCompared(final String realm) {
        return this.notCompared.getOrDefault(realm, List.of());
    }

    private static <T> Map<String, List<T>> copyOf(final Map<String, List<T>> byRealm) {
        Map<String, List<T>> copy = new LinkedHashMap<>();
        byRealm.forEach((realm, values) -> copy.put(realm, List.copyOf(values)));
        return java.util.Collections.unmodifiableMap(copy);
    }
}
