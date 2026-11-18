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

import java.util.Objects;

/** A modification to bring Keycloak to the desired state (ADR-004). */
public sealed interface Change<T extends Resource<?>> {

    /** The resource as it will be in Keycloak once the change is applied, or as it is before a deletion. */
    T resource();

    record Create<T extends Resource<?>>(T resource) implements Change<T> {
        public Create {
            Objects.requireNonNull(resource, "resource");
        }
    }

    /**
     * @param current the resource as it is in Keycloak
     * @param resource the resource as it will be after the update
     */
    record Update<T extends Resource<?>>(T current, T resource) implements Change<T> {
        public Update {
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(resource, "resource");
        }
    }

    record Delete<T extends Resource<?>>(T resource) implements Change<T> {
        public Delete {
            Objects.requireNonNull(resource, "resource");
        }
    }
}
