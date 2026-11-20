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

package io.github.doriangrelu.keycloak.config.application.normalization;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.List;
import java.util.Objects;

/**
 * An export once normalized.
 *
 * @param resources the resources to keep in the import file, per type; a type without resource to keep is absent
 * @param missing resources of the reference realm missing from the export: an import of the normalized file would not
 *     remove them
 */
public record NormalizedRealm(DesiredState resources, List<MissingResource> missing) {

    public NormalizedRealm {
        Objects.requireNonNull(resources, "resources");
        missing = List.copyOf(missing);
    }

    /** A resource of the reference realm missing from the export. */
    public record MissingResource(ResourceType type, String key) {}
}
