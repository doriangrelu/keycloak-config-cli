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

package io.github.doriangrelu.keycloak.config.application.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientSecretsTest {

    private static final String VALUE = "s3cr3t-value";

    @Test
    void holdsTheSecretsByName() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("shopSecret", VALUE);
        source.put("apiSecret", "other");

        ClientSecrets secrets = ClientSecrets.of(source);

        assertThat(secrets).containsExactly(Map.entry("apiSecret", "other"), Map.entry("shopSecret", VALUE));
        assertThat(secrets.get("shopSecret")).isEqualTo(VALUE);
        assertThat(secrets).isEqualTo(Map.of("shopSecret", VALUE, "apiSecret", "other"));
    }

    @Test
    void itsTextNeverShowsAValue() {
        ClientSecrets secrets = ClientSecrets.of(Map.of("shopSecret", VALUE));

        assertThat(secrets.toString()).isEqualTo("ClientSecrets[shopSecret]");
        assertThat(secrets.entrySet().toString()).doesNotContain(VALUE);
        assertThat(secrets.values().toString()).doesNotContain(VALUE).isEqualTo("[1 hidden value(s)]");
        assertThat(secrets.entrySet().iterator().next().toString()).isEqualTo("shopSecret");
    }

    @Test
    void theValuesCanStillBeRead() {
        ClientSecrets secrets = ClientSecrets.of(Map.of("shopSecret", VALUE));

        assertThat(secrets.values()).containsExactly(VALUE);
        assertThat(new java.util.ArrayList<>(secrets.values())).containsExactly(VALUE);
    }

    @Test
    void isReadOnly() {
        ClientSecrets secrets = ClientSecrets.of(Map.of("shopSecret", VALUE));

        assertThatThrownBy(() -> secrets.put("other", "x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> secrets.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
