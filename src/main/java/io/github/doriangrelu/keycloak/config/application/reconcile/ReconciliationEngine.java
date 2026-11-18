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

import io.github.doriangrelu.keycloak.config.domain.reconcile.Change;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Changes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Applies the desired state of a realm, one resource type at a time, in the order of their dependencies (ADR-004).
 *
 * <ul>
 *   <li>{@link #apply} creates and updates the resources declared by one import file. It runs at the place of the
 *       type in the import of each file, so that types not migrated yet can rely on them.
 *   <li>{@link #prune} deletes, once per realm and in reverse order, the resources missing from the merged desired
 *       state of all the files, as allowed by the deletion policy.
 * </ul>
 */
public final class ReconciliationEngine {

    private final List<Reconciler<?, ?>> ordered;

    public ReconciliationEngine(final Collection<? extends Reconciler<?, ?>> reconcilers) {
        this.ordered = inDependencyOrder(reconcilers);
    }

    /** Resource types in the order they are applied. */
    public List<ResourceType> order() {
        return this.ordered.stream().map(Reconciler::type).toList();
    }

    public void apply(final RealmContext context, final DesiredState part) {
        apply(context, part, EnumSet.allOf(ResourceType.class));
    }

    /**
     * Applies only some resource types of an import file, in dependency order: the legacy import calls the engine at
     * several places, so that the types it still handles find what they rely on.
     */
    public void apply(final RealmContext context, final DesiredState part, final Set<ResourceType> types) {
        for (Reconciler<?, ?> reconciler : this.ordered) {
            if (!types.contains(reconciler.type())) {
                continue;
            }
            part.resources(reconciler.type())
                    .ifPresent(declared -> applyType(context, reconciler, declared, part.scopes(reconciler.type())));
        }
    }

    /**
     * Adds the resources of an import file to the desired state of the realm without applying them: for a file whose
     * import is skipped because it did not change, so that the clean phase keeps its resources.
     */
    public void register(final RealmContext context, final DesiredState part) {
        for (Reconciler<?, ?> reconciler : this.ordered) {
            ResourceType type = reconciler.type();
            part.resources(type).ifPresent(declared -> context.declare(type, declared, part.scopes(type)));
        }
    }

    public void prune(final RealmContext context) {
        if (!context.startPruning()) {
            return;
        }
        for (Reconciler<?, ?> reconciler : this.ordered.reversed()) {
            if (prunes(context, reconciler) && context.isDeclared(reconciler.type())) {
                pruneType(context, reconciler);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, T extends Resource<K>> void applyType(
            final RealmContext context,
            final Reconciler<K, T> reconciler,
            final List<? extends Resource<?>> declared,
            final Set<String> scopes) {
        List<T> desired = (List<T>) declared;
        context.declare(reconciler.type(), desired, scopes);

        List<T> current = reconciler.read(context, desired);
        List<Change<T>> changes =
                Changes.toApply(desired, current, (actual, wanted) -> reconciler.merge(context, actual, wanted));
        run(context, changes, change -> {
            record(context, reconciler.type(), change);
            switch (change) {
                case Change.Create<T> creation -> reconciler.create(context, creation.resource());
                case Change.Update<T> update -> reconciler.update(context, update.current(), update.resource());
                case Change.Delete<T> deletion ->
                    throw new IllegalStateException("Unexpected deletion while applying " + deletion.resource());
            }
        });
    }

    private static <K, T extends Resource<K>> void pruneType(
            final RealmContext context, final Reconciler<K, T> reconciler) {
        List<T> desired = context.desired(reconciler.type());
        List<T> current = reconciler.read(context, desired);
        List<Change<T>> deletions = Changes.toPrune(
                desired,
                current,
                resource -> !reconciler.isOwnedByKeycloak(resource)
                        && context.deletionPolicy(
                                        reconciler.type(),
                                        reconciler.stateScope(resource),
                                        reconciler.usesRemoteState())
                                .allowsDeletionOf(resource.stateName()));
        run(context, deletions, deletion -> {
            record(context, reconciler.type(), deletion);
            reconciler.delete(context, deletion.resource());
        });
    }

    private static boolean prunes(final RealmContext context, final Reconciler<?, ?> reconciler) {
        return reconciler.prunes() && (context.isRemoteStateEnabled() || reconciler.prunesWithoutRemoteState());
    }

    private static <T extends Resource<?>> void run(
            final RealmContext context, final List<Change<T>> changes, final Consumer<Change<T>> action) {
        if (context.isParallel()) {
            changes.parallelStream().forEach(action);
        } else {
            changes.forEach(action);
        }
    }

    private static void record(final RealmContext context, final ResourceType type, final Change<?> change) {
        PlannedChange.Kind kind = switch (change) {
            case Change.Create<?> _ -> PlannedChange.Kind.CREATE;
            case Change.Update<?> _ -> PlannedChange.Kind.UPDATE;
            case Change.Delete<?> _ -> PlannedChange.Kind.DELETE;
        };
        context.record(new PlannedChange(type, change.resource().key(), kind));
    }

    /** Topological sort of the reconcilers; dependencies on types without reconciler are ignored. */
    private static List<Reconciler<?, ?>> inDependencyOrder(final Collection<? extends Reconciler<?, ?>> reconcilers) {
        Map<ResourceType, Reconciler<?, ?>> pending = new EnumMap<>(ResourceType.class);
        reconcilers.forEach(reconciler -> {
            if (pending.put(reconciler.type(), reconciler) != null) {
                throw new IllegalArgumentException("Several reconcilers for " + reconciler.type());
            }
        });

        List<Reconciler<?, ?>> ordered = new ArrayList<>();
        Map<ResourceType, Reconciler<?, ?>> remaining = new LinkedHashMap<>(pending);
        while (!remaining.isEmpty()) {
            List<ResourceType> ready = remaining.values().stream()
                    .filter(reconciler -> reconciler.dependsOn().stream().noneMatch(remaining::containsKey))
                    .map(Reconciler::type)
                    .toList();
            if (ready.isEmpty()) {
                throw new DependencyCycleException(remaining.keySet());
            }
            ready.forEach(type -> ordered.add(remaining.remove(type)));
        }
        return List.copyOf(ordered);
    }
}
