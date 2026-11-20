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

package io.github.doriangrelu.keycloak.config.domain.normalization;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Comparison of the resources of an export with the ones of the reference realm of its Keycloak version, a realm
 * Keycloak created and nobody changed. Normalizing an export keeps what differs from the reference: the rest is what
 * Keycloak creates by itself.
 *
 * @param <K> type of the key of the resources
 * @param <R> type of the resources
 * @param differing resources of the export missing from the reference, or different there, in the order of the export
 * @param missing keys of the resources of the reference missing from the export: an import would not remove them
 */
public record ReferenceComparison<K, R extends Resource<K>>(List<R> differing, List<K> missing) {

    public ReferenceComparison {
        differing = List.copyOf(differing);
        missing = List.copyOf(missing);
    }

    /** Compares the resources with {@link Object#equals}. */
    public static <K, R extends Resource<K>> ReferenceComparison<K, R> of(
            final Collection<R> exported, final Collection<R> reference) {
        return of(exported, reference, Object::equals);
    }

    /**
     * @param same whether a resource of the export, first, is the same as the resource of the reference with its key
     */
    public static <K, R extends Resource<K>> ReferenceComparison<K, R> of(
            final Collection<R> exported, final Collection<R> reference, final BiPredicate<R, R> same) {
        Map<K, R> referenceByKey = byKey(reference);
        Map<K, R> exportedByKey = byKey(exported);
        List<R> differing = new ArrayList<>();
        exportedByKey.forEach((key, resource) -> {
            R referenceResource = referenceByKey.get(key);
            if (referenceResource == null || !same.test(resource, referenceResource)) {
                differing.add(resource);
            }
        });
        List<K> missing = referenceByKey.keySet().stream()
                .filter(key -> !exportedByKey.containsKey(key))
                .toList();
        return new ReferenceComparison<>(differing, missing);
    }

    private static <K, R extends Resource<K>> Map<K, R> byKey(final Collection<R> resources) {
        Map<K, R> byKey = new LinkedHashMap<>();
        resources.forEach(resource -> byKey.putIfAbsent(resource.key(), resource));
        return byKey;
    }
}
