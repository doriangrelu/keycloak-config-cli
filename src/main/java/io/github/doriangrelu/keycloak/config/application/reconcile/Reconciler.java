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
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Brings one resource type of a realm to its desired state (ADR-004). The engine decides what to change; a
 * reconciler reads and writes through its port and knows the rules of its resource type.
 */
public interface Reconciler<K, T extends Resource<K>> {

    ResourceType type();

    /** Types whose resources must exist before this one is applied, and must be deleted after it. */
    Set<ResourceType> dependsOn();

    /** Current resources in Keycloak, restricted to the scope of {@code desired} when the type is scoped. */
    List<T> read(RealmContext context, Collection<T> desired);

    /** The resource as it should be after applying {@code desired}: fields omitted by the import keep their value. */
    T merge(T current, T desired);

    void create(RealmContext context, T resource);

    void update(RealmContext context, T current, T target);

    void delete(RealmContext context, T resource);

    /** Remote state entry where keycloak-config-cli records the resources of this type it created. */
    String stateScope(T resource);

    /** Resources created by Keycloak itself, such as default roles: never deleted. */
    boolean isOwnedByKeycloak(T resource);

    /**
     * Whether the clean phase deletes resources of this type. A type that only exists through another one, such as the
     * composites of a role, is never pruned: it disappears with its owner.
     */
    default boolean prunes() {
        return true;
    }
}
