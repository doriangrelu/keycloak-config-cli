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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import java.util.Map;
import java.util.Set;

/**
 * What Keycloak creates by itself in a realm, which a definition does not need to declare.
 *
 * @param clientIds the built-in clients
 * @param realmRoles the built-in realm roles
 * @param clientRoles the built-in roles of each client, by client identifier
 */
public record BuiltIns(Set<String> clientIds, Set<String> realmRoles, Map<String, Set<String>> clientRoles) {

    public BuiltIns {
        clientIds = Set.copyOf(clientIds);
        realmRoles = Set.copyOf(realmRoles);
        clientRoles = Map.copyOf(clientRoles);
    }

    public static BuiltIns none() {
        return new BuiltIns(Set.of(), Set.of(), Map.of());
    }
}
