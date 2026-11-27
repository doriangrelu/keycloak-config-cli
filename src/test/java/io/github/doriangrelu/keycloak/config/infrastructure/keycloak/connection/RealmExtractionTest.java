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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.BaselineProvider;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.NormalizationOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealmExtractionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MASK = RealmExtraction.MASK;

    @Test
    void aMaskedValueLeftIsRemovedWithAWarningThatNamesItsElement() throws Exception {
        ObjectNode document = (ObjectNode) JSON.readTree("""
                {
                  "realm": "shop",
                  "smtpServer": { "host": "smtp.example.org", "authTokenClientSecret": "%1$s" },
                  "clients": [ { "clientId": "app", "attributes": { "jwt.secret": "%1$s", "kept": "yes" } } ],
                  "components": {
                    "org.keycloak.keys.KeyProvider": [
                      { "name": "imported-rsa", "config": { "privateKey": [ "%1$s" ], "priority": [ "100" ] } }
                    ]
                  }
                }
                """.formatted(MASK));

        List<String> warnings = RealmExtraction.withoutMaskedValues(document, "shop");

        assertThat(warnings)
                .containsExactly(
                        "smtpServer.authTokenClientSecret of realm 'shop' is hidden by Keycloak and was not extracted",
                        "attributes.jwt.secret of client 'app' is hidden by Keycloak and was not extracted",
                        "config.privateKey of component 'imported-rsa' is hidden by Keycloak and was not extracted");
        assertThat(document.toString()).doesNotContain(MASK).contains("\"kept\":\"yes\"", "\"priority\"");
    }

    @Test
    void noWarningWithoutMaskedValue() throws Exception {
        ObjectNode document = (ObjectNode) JSON.readTree("{ \"realm\": \"shop\", \"enabled\": true }");

        assertThat(RealmExtraction.withoutMaskedValues(document, "shop")).isEmpty();
    }

    @Test
    void withoutVersionTheLatestReferenceIsUsed() {
        BaselineProvider baselines = new BaselineProvider(new ObjectMapper(), new NormalizationOptions(null, null));

        assertThat(RealmExtraction.referenceVersion(null, baselines)).isEqualTo(baselines.latestVersion());
        assertThat(RealmExtraction.referenceVersion(" ", baselines)).isEqualTo(baselines.latestVersion());
        assertThat(RealmExtraction.referenceVersion("26.4.0", baselines)).isEqualTo("26.4.0");
    }
}
