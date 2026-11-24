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
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariablesTest {

    @Test
    void variablesAreListedWithTheirDefaultsAndFiles() {
        List<VariableUse> uses = Variables.of(
                Map.of(
                        "00-realm.yaml", "realm: \"{{env}}-mon-realm\"\n",
                        "20-clients.yaml",
                                "realm: \"{{env}}-mon-realm\"\nclients:\n  - redirectUris: [\"{{appUrl:https://localhost}}/*\"]\n"));

        assertThat(uses)
                .containsExactly(
                        new VariableUse("appUrl", List.of("https://localhost"), List.of("20-clients.yaml")),
                        new VariableUse("env", List.of(), List.of("00-realm.yaml", "20-clients.yaml")));
    }

    @Test
    void aVariableInACommentIsListed() {
        List<VariableUse> uses = Variables.of(Map.of("00-realm.yaml", "# set {{secret}} at import\nrealm: x\n"));

        assertThat(uses).extracting(VariableUse::name).containsExactly("secret");
    }

    @Test
    void differentDefaultsAreAConflict() {
        assertThat(Variables.of(Map.of("a.yaml", "x: {{url:a}}", "b.yaml", "y: {{url:b}}"))
                        .getFirst()
                        .hasConflictingDefaults())
                .isTrue();
        assertThat(Variables.of(Map.of("a.yaml", "x: {{url:a}}", "b.yaml", "y: {{url:a}}"))
                        .getFirst()
                        .hasConflictingDefaults())
                .isFalse();
        assertThat(Variables.of(Map.of("a.yaml", "x: {{url:a}}", "b.yaml", "y: {{url}}"))
                        .getFirst()
                        .hasConflictingDefaults())
                .isFalse();
    }
}
