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

package io.github.doriangrelu.keycloak.config.domain.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StandardLayoutTest {

    @Test
    void eachKeyHasItsFile() {
        assertThat(StandardLayout.fileStemOf("clients")).isEqualTo("20-clients");
        assertThat(StandardLayout.fileStemOf("roles")).isEqualTo("30-roles");
        assertThat(StandardLayout.fileStemOf("groups")).isEqualTo("40-groups");
        assertThat(StandardLayout.fileStemOf("users")).isEqualTo("50-links");
        assertThat(StandardLayout.fileStemOf("scopeMappings")).isEqualTo("50-links");
        assertThat(StandardLayout.fileStemOf("clientScopeMappings")).isEqualTo("50-links");
        assertThat(StandardLayout.fileStemOf("defaultGroups")).isEqualTo("50-links");
        assertThat(StandardLayout.fileStemOf("identityProviderMappers")).isEqualTo("50-links");
        assertThat(StandardLayout.fileStemOf("defaultRole")).isEqualTo("50-links");
    }

    @Test
    void anyOtherKeyStaysInTheRealmFile() {
        assertThat(StandardLayout.fileStemOf("realm")).isEqualTo("00-realm");
        assertThat(StandardLayout.fileStemOf("clientScopes")).isEqualTo("00-realm");
        assertThat(StandardLayout.fileStemOf("authenticationFlows")).isEqualTo("00-realm");
    }

    @Test
    void theStemsAreInOrder() {
        assertThat(StandardLayout.stems())
                .isEqualTo(List.of("00-realm", "20-clients", "30-roles", "40-groups", "50-links"));
    }
}
