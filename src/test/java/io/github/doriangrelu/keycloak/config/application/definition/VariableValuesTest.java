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

import org.junit.jupiter.api.Test;

class VariableValuesTest {

    @Test
    void forgetEmptiesTheMemory() {
        VariableValues values = new VariableValues();
        values.remember("shopUrl", "https://shop.example.org");

        assertThat(values.get("shopUrl")).contains("https://shop.example.org");
        values.forget();

        assertThat(values.get("shopUrl")).isEmpty();
    }

    @Test
    void toStringShowsNoValue() {
        VariableValues values = new VariableValues();
        values.remember("shopSecret", "marker-s3cr3t");

        assertThat(values.toString()).doesNotContain("marker-s3cr3t");
    }
}
