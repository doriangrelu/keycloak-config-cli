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

package io.github.doriangrelu.keycloak.config.domain.clientscope.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientScopeTest {

    private static ProtocolMapper mapper(String name, String type, Map<String, String> config) {
        return new ProtocolMapper(name, "openid-connect", type, config, null);
    }

    private static ClientScope scope(String description, Map<String, String> attributes, List<ProtocolMapper> mappers) {
        return new ClientScope("my_scope", description, "openid-connect", attributes, mappers, false, null);
    }

    @Test
    void unspecifiedFieldsKeepTheirValue() {
        ClientScope current = scope("desc", Map.of("a", "1"), List.of(mapper("m", "t", Map.of("k", "v"))));

        assertThat(current.mergedWith(new ClientScope("my_scope", null, null, null, null, false, null)))
                .isEqualTo(current);
    }

    @Test
    void attributesAreMergedKeyByKey() {
        ClientScope current = scope(null, Map.of("a", "1", "b", "2"), null);

        assertThat(current.mergedWith(scope(null, Map.of("b", "3"), null)).attributes())
                .containsExactlyInAnyOrderEntriesOf(Map.of("a", "1", "b", "3"));
    }

    @Test
    void declaredMappersAreTheExactSetAndCompleteTheExistingOnes() {
        ClientScope current =
                scope(null, null, List.of(mapper("kept", "t", Map.of("k1", "v1")), mapper("removed", "t", Map.of())));

        ClientScope merged = current.mergedWith(scope(
                null, null, List.of(mapper("kept", null, Map.of("k2", "v2")), mapper("added", "t", null))));

        assertThat(merged.protocolMappers())
                .containsExactlyInAnyOrder(
                        mapper("kept", "t", Map.of("k1", "v1", "k2", "v2")), mapper("added", "t", null));
    }

    @Test
    void theRealmDefaultFlagComesFromKeycloak() {
        ClientScope current = new ClientScope("profile", null, null, null, null, true, null);

        assertThat(current.mergedWith(new ClientScope("profile", "new", null, null, null, false, null))
                        .realmDefault())
                .isTrue();
    }
}
