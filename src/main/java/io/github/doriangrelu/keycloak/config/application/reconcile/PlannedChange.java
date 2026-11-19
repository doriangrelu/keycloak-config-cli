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

package io.github.doriangrelu.keycloak.config.application.reconcile;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;

/**
 * A change applied, or to apply, to a realm: the entries of the reconciliation plan (ADR-004), kept for the report
 * and the dry run.
 *
 * @param before the resource in Keycloak, {@code null} for a creation
 * @param after the resource once the change is applied, {@code null} for a deletion
 */
public record PlannedChange(ResourceType type, Object key, Kind kind, Resource<?> before, Resource<?> after) {

    public enum Kind {
        CREATE,
        UPDATE,
        DELETE
    }
}
