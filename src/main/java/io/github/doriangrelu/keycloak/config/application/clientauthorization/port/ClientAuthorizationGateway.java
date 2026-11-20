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

package io.github.doriangrelu.keycloak.config.application.clientauthorization.port;

import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import java.util.Optional;
import java.util.Set;

/** Authorization services of the clients of a realm in Keycloak. */
public interface ClientAuthorizationGateway {

    /**
     * What the import may delete of the authorization of a client: the resources, policies and scopes it misses, the
     * resources limited to those keycloak-config-cli created when the remote state is enabled.
     */
    record Deletions(Optional<Set<String>> managedResources) {

        /**
         * @param managedResources resources keycloak-config-cli created, when the remote state is enabled; the
         *     {@code Default Resource} of Keycloak is always managed
         */
        public Deletions {
            managedResources = managedResources == null ? Optional.empty() : managedResources;
        }
    }

    /** Current authorization of a declared client; empty when the client does not exist. */
    Optional<ClientAuthorization> find(String realm, ClientAuthorization client);

    /**
     * Applies the declared authorization of a client: resolves its placeholders, enables the fine-grained permissions
     * of the objects it names for {@code realm-management}, then writes the settings, resources, scopes and policies
     * that changed, and deletes those the import misses as {@code deletions} allows.
     */
    void apply(String realm, ClientAuthorization target, Deletions deletions);
}
