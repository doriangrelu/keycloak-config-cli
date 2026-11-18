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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientProfile;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientPolicyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientPolicyRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;

class ClientPolicyKeycloakGatewayTest {

    static final class InMemoryClientPolicies implements ClientPolicyApi {
        List<ClientProfileRepresentation> profiles = new ArrayList<>();
        List<ClientPolicyRepresentation> policies = new ArrayList<>();
        int writes;

        @Override
        public List<ClientProfileRepresentation> listProfiles(String realm) {
            return List.copyOf(this.profiles);
        }

        @Override
        public void replaceProfiles(String realm, List<ClientProfileRepresentation> profiles) {
            this.writes++;
            this.profiles = new ArrayList<>(profiles);
        }

        @Override
        public List<ClientPolicyRepresentation> listPolicies(String realm) {
            return List.copyOf(this.policies);
        }

        @Override
        public void replacePolicies(String realm, List<ClientPolicyRepresentation> policies) {
            this.writes++;
            this.policies = new ArrayList<>(policies);
        }
    }

    private final InMemoryClientPolicies keycloak = new InMemoryClientPolicies();
    private final ClientPolicyKeycloakGateway gateway = new ClientPolicyKeycloakGateway(
            this.keycloak, new ClientPolicyKeycloakMapper(new UnmodeledAttributesConverter()));

    private static ClientProfile profile(String name, String description) {
        return new ClientProfile(name, new UnmodeledAttributes(Map.of("description", description)));
    }

    @Test
    void aSavedProfileReplacesTheOneWithTheSameNameAndKeepsTheOthers() {
        this.gateway.saveProfile("realm", profile("a", "first"));
        this.gateway.saveProfile("realm", profile("b", "second"));

        this.gateway.saveProfile("realm", profile("a", "changed"));

        assertThat(this.gateway.findAllProfiles("realm"))
                .containsExactly(profile("a", "changed"), profile("b", "second"));
    }

    @Test
    void deletingAMissingProfileWritesNothing() {
        this.gateway.saveProfile("realm", profile("a", "first"));

        this.gateway.deleteProfile("realm", "missing");
        this.gateway.deleteProfile("realm", "a");

        assertThat(this.keycloak.profiles).isEmpty();
        assertThat(this.keycloak.writes).isEqualTo(2);
    }
}
