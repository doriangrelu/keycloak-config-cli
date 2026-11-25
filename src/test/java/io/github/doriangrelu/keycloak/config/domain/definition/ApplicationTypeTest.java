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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The application types and their flows: the table of ADR-008. */
class ApplicationTypeTest {

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(ApplicationType.SPA, new ClientFlows(true, true, false, false, false)),
                Arguments.of(ApplicationType.WEB_APPLICATION, new ClientFlows(false, true, false, false, false)),
                Arguments.of(ApplicationType.SERVICE, new ClientFlows(false, false, false, false, true)),
                Arguments.of(ApplicationType.API, new ClientFlows(false, false, false, false, false)));
    }

    @ParameterizedTest
    @MethodSource("table")
    void eachTypeHasExactlyItsFlows(final ApplicationType type, final ClientFlows flows) {
        assertThat(ApplicationType.of(flows)).isEqualTo(type);
        assertThat(ClientFlows.of(type)).isEqualTo(flows);
    }

    @Test
    void anyOtherCombinationIsCustom() {
        // A web application with direct access grants: the defaults of Keycloak
        assertThat(ApplicationType.of(new ClientFlows(false, true, false, true, false)))
                .isEqualTo(ApplicationType.CUSTOM);
        assertThat(ApplicationType.of(ClientFlows.KEYCLOAK_DEFAULTS)).isEqualTo(ApplicationType.CUSTOM);
    }

    @Test
    void aCustomClientHasNoFlowsOfItsOwn() {
        assertThatThrownBy(() -> ClientFlows.of(ApplicationType.CUSTOM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionNamesRoundTrip() {
        for (ApplicationType type : ApplicationType.values()) {
            assertThat(ApplicationType.ofOptionName(type.optionName())).contains(type);
        }
        assertThat(ApplicationType.SPA.optionName()).isEqualTo("spa");
        assertThat(ApplicationType.WEB_APPLICATION.optionName()).isEqualTo("web-application");
        assertThat(ApplicationType.ofOptionName("WEB-APPLICATION")).contains(ApplicationType.WEB_APPLICATION);
        assertThat(ApplicationType.ofOptionName("frontend")).isEmpty();
    }
}
