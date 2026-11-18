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

package io.github.doriangrelu.keycloak.config.application.clientpolicy.port;

import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientPolicy;
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientProfile;
import java.util.List;

/**
 * Client profiles and client policies of a realm in Keycloak. Keycloak only writes them as whole lists: each operation
 * reads the list, changes one element and writes the list back.
 */
public interface ClientPolicyGateway {

    /** Profiles of the realm, without the global profiles of Keycloak. */
    List<ClientProfile> findAllProfiles(String realm);

    /** Creates or replaces the profile with the same name. */
    void saveProfile(String realm, ClientProfile profile);

    void deleteProfile(String realm, String name);

    List<ClientPolicy> findAllPolicies(String realm);

    /** Creates or replaces the policy with the same name. */
    void savePolicy(String realm, ClientPolicy policy);

    void deletePolicy(String realm, String name);
}
