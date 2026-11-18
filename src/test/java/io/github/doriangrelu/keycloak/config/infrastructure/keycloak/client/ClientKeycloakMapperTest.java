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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapperImpl;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.springframework.test.util.ReflectionTestUtils;

class ClientKeycloakMapperTest {

    @Test
    void writesWhatItReads() {
        UnmodeledAttributesConverter converter = new UnmodeledAttributesConverter();
        ClientScopeKeycloakMapperImpl protocolMappers = new ClientScopeKeycloakMapperImpl();
        ReflectionTestUtils.invokeMethod(protocolMappers, "setUnmodeledAttributes", converter);
        ClientKeycloakMapperImpl mapper = new ClientKeycloakMapperImpl();
        ReflectionTestUtils.invokeMethod(mapper, "setUnmodeledAttributes", converter);
        ReflectionTestUtils.setField(mapper, "clientScopeKeycloakMapper", protocolMappers);

        ProtocolMapperRepresentation protocolMapper = new ProtocolMapperRepresentation();
        protocolMapper.setName("full name");
        protocolMapper.setProtocol("openid-connect");
        protocolMapper.setProtocolMapper("oidc-full-name-mapper");
        protocolMapper.setConfig(Map.of("id.token.claim", "true"));
        ClientRepresentation representation = new ClientRepresentation();
        representation.setClientId("app");
        representation.setName("App");
        representation.setDescription("An application");
        representation.setEnabled(true);
        representation.setRedirectUris(List.of("https://app/*"));
        representation.setAttributes(Map.of("pkce.code.challenge.method", "S256"));
        representation.setDefaultClientScopes(List.of("profile"));
        representation.setProtocolMappers(List.of(protocolMapper));
        representation.setPublicClient(false);

        Client client = mapper.toClient(representation);
        ClientRepresentation written = mapper.toRepresentation(client);

        assertThat(client.unmodeled().values()).containsKeys("description", "enabled", "redirectUris");
        assertThat(written).usingRecursiveComparison().isEqualTo(representation);
    }
}
