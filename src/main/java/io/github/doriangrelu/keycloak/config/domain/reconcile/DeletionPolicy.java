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

import io.github.doriangrelu.keycloak.config.domain.common.ManagedMode;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a resource missing from the import may be deleted.
 *
 * <p>A resource is deleted only if all of these hold:
 *
 * <ul>
 *   <li>its type is managed in {@link ManagedMode#FULL} mode;
 *   <li>when the remote state is enabled, keycloak-config-cli created it: its name is in the state recorded before
 *       the run;
 *   <li>no protection rule matches its name.
 * </ul>
 *
 * <p>Resources created by Keycloak itself (default roles, built-in clients...) are excluded by each resource type.
 *
 * @param managedState names recorded in the remote state before the run, or empty when the remote state is disabled
 */
public record DeletionPolicy(ManagedMode mode, Optional<Set<String>> managedState, List<ProtectionRule> protections) {

    public DeletionPolicy {
        Objects.requireNonNull(mode, "mode");
        managedState = managedState.map(Set::copyOf);
        protections = List.copyOf(protections);
    }

    public boolean allowsDeletionOf(final String name) {
        if (this.mode != ManagedMode.FULL) {
            return false;
        }
        if (this.managedState.isPresent() && !this.managedState.get().contains(name)) {
            return false;
        }
        return this.protections.stream().noneMatch(rule -> rule.protects(name));
    }
}
