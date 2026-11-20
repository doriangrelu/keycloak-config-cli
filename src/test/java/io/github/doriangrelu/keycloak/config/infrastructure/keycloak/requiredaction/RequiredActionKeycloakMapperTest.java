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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;

class RequiredActionKeycloakMapperTest {

    @Test
    void writesWhatItReads() {
        RequiredActionKeycloakMapperImpl mapper = new RequiredActionKeycloakMapperImpl();
        mapper.setUnmodeledAttributes(new UnmodeledAttributesConverter());
        RequiredActionProviderRepresentation representation = new RequiredActionProviderRepresentation();
        representation.setAlias("CONFIGURE_TOTP");
        representation.setName("Configure OTP");
        representation.setProviderId("CONFIGURE_TOTP");
        representation.setEnabled(true);
        representation.setDefaultAction(true);
        representation.setPriority(10);
        representation.setConfig(Map.of("key", "value"));

        RequiredAction requiredAction = mapper.toRequiredAction(representation);
        RequiredActionProviderRepresentation written = mapper.toRepresentation(requiredAction);

        assertThat(requiredAction.protectedName()).isEqualTo("CONFIGURE_TOTP");
        assertThat(requiredAction.unmodeled().isEmpty()).isTrue();
        assertThat(written).usingRecursiveComparison().isEqualTo(representation);
    }
}
