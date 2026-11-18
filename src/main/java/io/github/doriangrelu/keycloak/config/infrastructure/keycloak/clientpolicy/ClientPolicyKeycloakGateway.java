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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy;

import io.github.doriangrelu.keycloak.config.application.clientpolicy.port.ClientPolicyGateway;
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientPolicy;
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientProfile;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientPolicyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link ClientPolicyGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ClientPolicyKeycloakGateway implements ClientPolicyGateway {

    private final ClientPolicyApi clientPolicyApi;
    private final ClientPolicyKeycloakMapper mapper;

    public ClientPolicyKeycloakGateway(final ClientPolicyApi clientPolicyApi, final ClientPolicyKeycloakMapper mapper) {
        this.clientPolicyApi = clientPolicyApi;
        this.mapper = mapper;
    }

    @Override
    public List<ClientProfile> findAllProfiles(final String realm) {
        return this.clientPolicyApi.listProfiles(realm).stream()
                .map(this.mapper::toProfile)
                .toList();
    }

    @Override
    public void saveProfile(final String realm, final ClientProfile profile) {
        this.clientPolicyApi.replaceProfiles(
                realm,
                saved(
                        this.clientPolicyApi.listProfiles(realm),
                        this.mapper.toRepresentation(profile),
                        representation -> representation.getName()));
    }

    @Override
    public void deleteProfile(final String realm, final String name) {
        var profiles = new ArrayList<>(this.clientPolicyApi.listProfiles(realm));
        if (profiles.removeIf(profile -> Objects.equals(profile.getName(), name))) {
            this.clientPolicyApi.replaceProfiles(realm, profiles);
        }
    }

    @Override
    public List<ClientPolicy> findAllPolicies(final String realm) {
        return this.clientPolicyApi.listPolicies(realm).stream()
                .map(this.mapper::toPolicy)
                .toList();
    }

    @Override
    public void savePolicy(final String realm, final ClientPolicy policy) {
        this.clientPolicyApi.replacePolicies(
                realm,
                saved(
                        this.clientPolicyApi.listPolicies(realm),
                        this.mapper.toRepresentation(policy),
                        representation -> representation.getName()));
    }

    @Override
    public void deletePolicy(final String realm, final String name) {
        var policies = new ArrayList<>(this.clientPolicyApi.listPolicies(realm));
        if (policies.removeIf(policy -> Objects.equals(policy.getName(), name))) {
            this.clientPolicyApi.replacePolicies(realm, policies);
        }
    }

    /** The list with {@code element} in place of the element of the same name, or appended. */
    private static <T> List<T> saved(final List<T> current, final T element, final Function<T, String> name) {
        List<T> result = new ArrayList<>(current);
        for (int index = 0; index < result.size(); index++) {
            if (Objects.equals(name.apply(result.get(index)), name.apply(element))) {
                result.set(index, element);
                return result;
            }
        }
        result.add(element);
        return result;
    }
}
