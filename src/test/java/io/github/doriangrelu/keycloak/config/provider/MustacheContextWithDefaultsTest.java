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

package io.github.doriangrelu.keycloak.config.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MustacheContextWithDefaultsTest {

    private final MustacheContextWithDefaults context = new MustacheContextWithDefaults(Map.of("host", "sso"));

    @Test
    void aDefinedVariableIsFound() {
        assertThat(this.context.containsKey("host")).isTrue();
        assertThat(this.context.get("host")).isEqualTo("sso");
    }

    @Test
    void aDefinedVariableWinsOverItsDefaultValue() {
        assertThat(this.context.get("host:localhost")).isEqualTo("sso");
    }

    @Test
    void theDefaultValueIsUsedForAnUndefinedVariable() {
        assertThat(this.context.containsKey("port:8080")).isTrue();
        assertThat(this.context.get("port:8080")).isEqualTo("8080");
    }

    @Test
    void theDefaultValueMayHoldColons() {
        assertThat(this.context.get("url:http://localhost:8080")).isEqualTo("http://localhost:8080");
    }

    @Test
    void anUndefinedVariableWithoutDefaultIsNotFound() {
        assertThat(this.context.containsKey("port")).isFalse();
        assertThat(this.context.get("port")).isNull();
    }

    @Test
    void theEntriesAreTheDefinedVariables() {
        assertThat(this.context).containsExactly(Map.entry("host", "sso"));
    }
}
