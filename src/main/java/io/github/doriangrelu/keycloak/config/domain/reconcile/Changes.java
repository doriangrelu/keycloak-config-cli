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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Diff between the desired and the actual state of one resource type (ADR-004). */
public final class Changes {

    private Changes() {}

    /**
     * Creations and updates needed to reach {@code desired}. Resources are paired by key.
     *
     * @param merge applies a desired resource on top of the actual one; the update is planned only if the result
     *     differs from the actual resource
     */
    public static <K, T extends Resource<K>> List<Change<T>> toApply(
            final Collection<T> desired, final Collection<T> actual, final BinaryOperator<T> merge) {
        Map<K, T> actualByKey = byKey(actual);
        List<Change<T>> changes = new ArrayList<>();
        for (T wanted : desired) {
            T current = actualByKey.get(wanted.key());
            if (current == null) {
                changes.add(new Change.Create<>(wanted));
                continue;
            }
            T target = merge.apply(current, wanted);
            if (!target.equals(current)) {
                changes.add(new Change.Update<>(current, target));
            }
        }
        return changes;
    }

    /**
     * Deletions of the actual resources missing from {@code desired} that {@code deletable} allows to delete.
     */
    public static <K, T extends Resource<K>> List<Change<T>> toPrune(
            final Collection<T> desired, final Collection<T> actual, final Predicate<T> deletable) {
        Map<K, T> desiredByKey = byKey(desired);
        return actual.stream()
                .filter(current -> !desiredByKey.containsKey(current.key()))
                .filter(deletable)
                .<Change<T>>map(Change.Delete::new)
                .toList();
    }

    private static <K, T extends Resource<K>> Map<K, T> byKey(final Collection<T> resources) {
        return resources.stream()
                .collect(Collectors.toMap(
                        Resource::key, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }
}
