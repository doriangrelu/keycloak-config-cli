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

package io.github.doriangrelu.keycloak.config.domain.reconcile;

import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import java.util.List;

/**
 * Decides whether a resource missing from the import files may be deleted (ADR-007): unless a protection rule of the
 * files matches its name, by equality or by pattern.
 *
 * <p>The other guarantees are kept by each resource type: users are never deleted, nor the resources Keycloak creates
 * (default roles, built-in clients...).
 */
public record DeletionPolicy(List<ProtectionRule> protections) {

    public DeletionPolicy {
        protections = List.copyOf(protections);
    }

    public boolean allowsDeletionOf(final String name) {
        return this.protections.stream().noneMatch(rule -> rule.protects(name));
    }
}
