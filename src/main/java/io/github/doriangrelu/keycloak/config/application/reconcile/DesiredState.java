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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resources declared by one import file, per type. A type absent from the state is not declared by the file; a type
 * declared with an empty list asks to remove every managed resource of that type.
 */
public final class DesiredState {

    private final Map<ResourceType, List<? extends Resource<?>>> resources = new EnumMap<>(ResourceType.class);

    public DesiredState declare(final ResourceType type, final List<? extends Resource<?>> declared) {
        this.resources.merge(type, List.copyOf(declared), (first, second) -> {
            throw new IllegalArgumentException(type + " is declared twice in the same desired state");
        });
        return this;
    }

    public Optional<List<? extends Resource<?>>> resources(final ResourceType type) {
        return Optional.ofNullable(this.resources.get(type));
    }

    public boolean isEmpty() {
        return this.resources.isEmpty();
    }
}
