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

package io.github.doriangrelu.keycloak.config.domain.identityprovider.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityProviderTest {

    @Test
    void undeclaredLoginFlowsAreReset() {
        IdentityProvider current = new IdentityProvider("idp", "my first login", "my post login", null);

        IdentityProvider merged = current.mergedWith(new IdentityProvider("idp", null, null, null));

        assertThat(merged.firstBrokerLoginFlowAlias()).isEqualTo(IdentityProvider.DEFAULT_FIRST_BROKER_LOGIN_FLOW);
        assertThat(merged.postBrokerLoginFlowAlias()).isNull();
    }

    @Test
    void aDeclaredConfigReplacesTheCurrentOne() {
        IdentityProvider current = new IdentityProvider(
                "idp",
                null,
                null,
                new UnmodeledAttributes(Map.of("enabled", true, "config", Map.of("a", "1", "b", "2"))));

        IdentityProvider merged = current.mergedWith(
                new IdentityProvider("idp", null, null, new UnmodeledAttributes(Map.of("config", Map.of("b", "3")))));

        assertThat(merged.unmodeled().values())
                .containsExactlyInAnyOrderEntriesOf(Map.of("enabled", true, "config", Map.of("b", "3")));
    }
}
