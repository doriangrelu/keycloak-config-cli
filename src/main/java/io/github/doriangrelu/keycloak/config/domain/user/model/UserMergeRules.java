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

package io.github.doriangrelu.keycloak.config.domain.user.model;

import java.util.Set;

/**
 * What an import keeps on an existing user although the file does not declare it.
 *
 * @param keptRealmRoles realm roles never removed from the user, such as the default roles of the realm
 * @param keycloakClients clients created by Keycloak: their role mappings are only managed when the file declares them
 * @param keepAttributes whether the attributes of the user are left as they are (federated users)
 */
public record UserMergeRules(Set<String> keptRealmRoles, Set<String> keycloakClients, boolean keepAttributes) {

    public UserMergeRules {
        keptRealmRoles = Set.copyOf(keptRealmRoles);
        keycloakClients = Set.copyOf(keycloakClients);
    }
}
