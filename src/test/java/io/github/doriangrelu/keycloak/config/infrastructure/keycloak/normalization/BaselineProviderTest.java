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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.exception.NormalizationException;
import io.github.doriangrelu.keycloak.config.properties.NormalizationConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaselineProviderTest {

    private static BaselineProvider provider(final String fallbackVersion) {
        return new BaselineProvider(
                new ObjectMapper(),
                new NormalizationConfigProperties(
                        null, NormalizationConfigProperties.OutputFormat.YAML, fallbackVersion));
    }

    @ParameterizedTest
    @CsvSource({
        // The version itself
        "26.5.3, 26.5.3",
        "19.0.3, 19.0.3",
        // The greatest lower version of the same major version
        "26.4.0, 26.0.5",
        "26.9.9, 26.5.3",
        // Else the lowest greater version of the same major version
        "26.0.0, 26.0.5",
        // Else the greatest lower version
        "24.0.5, 20.0.3",
        "30.0.0, 26.5.3"
    })
    void usesTheClosestReference(final String version, final String reference) {
        assertThat(provider(null).baselineVersion(version)).isEqualTo(reference);
    }

    @Test
    void usesTheFallbackVersionWhenSet() {
        assertThat(provider("19.0.3").baselineVersion("26.4.0")).isEqualTo("19.0.3");
        assertThat(provider("19.0.3").baselineVersion("26.5.3")).isEqualTo("26.5.3");
        assertThatThrownBy(() -> provider("1.0.0").baselineVersion("26.4.0"))
                .isInstanceOf(NormalizationException.class)
                .hasMessageContaining("fallback version 1.0.0 does not exist");
    }

    @Test
    void failsWithoutLowerReference() {
        assertThatThrownBy(() -> provider(null).baselineVersion("18.0.0"))
                .isInstanceOf(NormalizationException.class)
                .hasMessage("Reference realm for version 18.0.0 does not exist. Aborting!");
    }

    @Test
    void namesTheReferenceRealmAfterTheExportedOne() {
        var realm = provider(null).getRealm("26.5.3", "acme");

        assertThat(realm.getRealm()).isEqualTo("acme");
        assertThat(realm.getKeycloakVersion()).isEqualTo("26.5.3");
        assertThat(provider(null).getClient("26.5.3", "app").getClientId()).isEqualTo("app");
    }
}
