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

import io.github.doriangrelu.keycloak.config.domain.common.ConflictingDefinitionException;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.reconcile.DeletionPolicy;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the engine knows about one realm during one run (ADR-004): the merged desired state of all its import
 * files, the remote state recorded before the run, the managed modes, the protection rules and the plan. It replaces
 * the global execution context for the migrated resource types, and is passed explicitly.
 */
public final class RealmContext {

    private final String realm;
    private final boolean parallel;
    private final boolean remoteStateEnabled;
    private final boolean dryRun;
    private volatile boolean realmMissing;

    private final List<ProtectionRule> protections = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Set<String>> previousState = new ConcurrentHashMap<>();
    private final Map<ResourceType, Map<Object, Resource<?>>> desired = new EnumMap<>(ResourceType.class);
    private final Map<ResourceType, Set<String>> scopes = new EnumMap<>(ResourceType.class);
    private final List<PlannedChange> plan = Collections.synchronizedList(new ArrayList<>());
    private boolean pruned;

    public RealmContext(final String realm, final boolean parallel, final boolean remoteStateEnabled) {
        this(realm, parallel, remoteStateEnabled, false);
    }

    /** @param dryRun whether the changes are only planned, never applied (EPIC-004) */
    public RealmContext(
            final String realm, final boolean parallel, final boolean remoteStateEnabled, final boolean dryRun) {
        this.dryRun = dryRun;
        this.realm = Objects.requireNonNull(realm, "realm");
        this.parallel = parallel;
        this.remoteStateEnabled = remoteStateEnabled;
    }

    public String realm() {
        return this.realm;
    }

    public boolean isDryRun() {
        return this.dryRun;
    }

    /**
     * Whether the realm does not exist in Keycloak during a dry run: it is not created, so nothing can be read from
     * it, and everything the import declares is planned as a creation.
     */
    public boolean isRealmMissing() {
        return this.realmMissing;
    }

    /** Records that the realm does not exist; only meaningful in a dry run, where it is never created. */
    public void realmIsMissing() {
        this.realmMissing = true;
    }

    public boolean isParallel() {
        return this.parallel;
    }

    public boolean isRemoteStateEnabled() {
        return this.remoteStateEnabled;
    }

    public void addProtections(final Collection<ProtectionRule> rules) {
        this.protections.addAll(rules);
    }

    /**
     * Records the remote state of a scope the first time it is seen in the run: later import files of the same run
     * overwrite the state, and the clean phase needs it as it was before the run.
     */
    public void captureState(final String scope, final Collection<String> names) {
        this.previousState.putIfAbsent(scope, Set.copyOf(names));
    }

    /**
     * Names the remote state recorded for a scope before the run, when the remote state is enabled: for reconcilers
     * whose resources hold parts deleted on their own rules, such as the resources of a client authorization.
     */
    public Optional<Set<String>> managedState(final String scope) {
        return this.remoteStateEnabled
                ? Optional.of(this.previousState.getOrDefault(scope, Set.of()))
                : Optional.empty();
    }

    /** Whether a protection rule of the import files covers this name. */
    public boolean isProtected(final String name) {
        synchronized (this.protections) {
            return this.protections.stream().anyMatch(rule -> rule.protects(name));
        }
    }

    public DeletionPolicy deletionPolicy(final ResourceType type, final String stateScope) {
        return deletionPolicy(type, stateScope, true);
    }

    /** @param usesRemoteState whether the type limits its deletions to what the remote state records */
    public DeletionPolicy deletionPolicy(
            final ResourceType type, final String stateScope, final boolean usesRemoteState) {
        Optional<Set<String>> managedState = this.remoteStateEnabled && usesRemoteState
                ? Optional.of(this.previousState.getOrDefault(stateScope, Set.of()))
                : Optional.empty();
        return new DeletionPolicy(managedState, List.copyOf(this.protections));
    }

    /**
     * Adds resources declared by an import file to the desired state of the realm.
     *
     * @throws ConflictingDefinitionException when a key is already declared with a different content
     */
    public synchronized void declare(final ResourceType type, final Collection<? extends Resource<?>> resources) {
        declare(type, resources, Set.of());
    }

    /** Same as {@link #declare(ResourceType, Collection)}, with the scopes the file declares for a scoped type. */
    public synchronized void declare(
            final ResourceType type,
            final Collection<? extends Resource<?>> resources,
            final Set<String> declaredScopes) {
        this.scopes.computeIfAbsent(type, unused -> new LinkedHashSet<>()).addAll(declaredScopes);
        Map<Object, Resource<?>> declared = this.desired.computeIfAbsent(type, unused -> new LinkedHashMap<>());
        for (Resource<?> resource : resources) {
            Resource<?> previous = declared.putIfAbsent(resource.key(), resource);
            if (previous != null && !previous.equals(resource)) {
                throw new ConflictingDefinitionException(this.realm, type.label(), resource.key());
            }
        }
    }

    /** Scopes declared by the import files for a scoped type, such as the clients of the client roles. */
    public synchronized Set<String> scopes(final ResourceType type) {
        return Set.copyOf(this.scopes.getOrDefault(type, Set.of()));
    }

    public synchronized boolean isDeclared(final ResourceType type) {
        return this.desired.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    synchronized <T extends Resource<?>> List<T> desired(final ResourceType type) {
        return this.desired.getOrDefault(type, Map.of()).values().stream()
                .map(resource -> (T) resource)
                .toList();
    }

    void record(final PlannedChange change) {
        this.plan.add(change);
    }

    public List<PlannedChange> plan() {
        synchronized (this.plan) {
            return List.copyOf(this.plan);
        }
    }

    /** @return {@code true} the first time only: the clean phase runs once per realm and run */
    synchronized boolean startPruning() {
        if (this.pruned) {
            return false;
        }
        this.pruned = true;
        return true;
    }
}
