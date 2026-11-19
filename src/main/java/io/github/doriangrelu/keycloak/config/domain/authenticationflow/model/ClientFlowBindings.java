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

package io.github.doriangrelu.keycloak.config.domain.authenticationflow.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The flows a client uses instead of the flows of its realm ({@code authenticationFlowBindingOverrides}), by binding
 * ({@code browser}, {@code direct_grant}). A flow is given by its alias, or by its id. Identified like the client: its
 * client id, or {@code name:<name>} for a client only known by its name.
 *
 * <p>The declared bindings are the exact bindings of the client: a declared client without bindings loses them all.
 */
public record ClientFlowBindings(String clientId, String name, Map<String, String> overrides)
        implements Resource<String> {

    private static final String NAME_PREFIX = "name:";

    public ClientFlowBindings {
        if (clientId == null && name == null) {
            throw new IllegalArgumentException("A client needs a client id or a name");
        }
        overrides = overrides == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
    }

    @Override
    public String key() {
        return this.clientId != null ? this.clientId : NAME_PREFIX + this.name;
    }

    @Override
    public String stateName() {
        return key();
    }

    public ClientFlowBindings withOverrides(final Map<String, String> replaced) {
        return new ClientFlowBindings(this.clientId, this.name, Objects.requireNonNull(replaced, "replaced"));
    }
}
