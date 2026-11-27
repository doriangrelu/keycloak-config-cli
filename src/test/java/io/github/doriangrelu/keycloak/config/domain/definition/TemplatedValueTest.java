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

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplatedValueTest {

    @Test
    void aVariableInsideAValueIsFound() {
        TemplatedValue value = TemplatedValue.of("{{appUrl}}/*");

        assertThat(value.isTemplated()).isTrue();
        assertThat(value.isFullyVariable()).isFalse();
        assertThat(value.variables()).containsExactly(new TemplatedValue.Variable("appUrl", Optional.empty()));
    }

    @Test
    void theDefaultValueIsEverythingAfterTheFirstColon() {
        assertThat(TemplatedValue.of("{{appUrl:https://localhost:8080}}").variables())
                .containsExactly(new TemplatedValue.Variable("appUrl", Optional.of("https://localhost:8080")));
    }

    @Test
    void aComposedValueIsNotFullyVariable() {
        TemplatedValue value = TemplatedValue.of("{{env}}-mon-realm");

        assertThat(value.isFullyVariable()).isFalse();
        assertThat(value.variables()).extracting(TemplatedValue.Variable::name).containsExactly("env");
    }

    @Test
    void aValueMadeOfOneVariableIsFullyVariable() {
        assertThat(TemplatedValue.of("{{url}}").isFullyVariable()).isTrue();
        assertThat(TemplatedValue.of("{{url:https://example.org}}").isFullyVariable())
                .isTrue();
    }

    @Test
    void aPlainValueIsNotTemplated() {
        TemplatedValue value = TemplatedValue.of("plain");

        assertThat(value.isTemplated()).isFalse();
        assertThat(value.variables()).isEmpty();
        assertThat(value.raw()).isEqualTo("plain");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{a", "{{}}", "{{{a}}}", "{{1a}}", "a}}b", "{{a b}}"})
    void anInvalidPlaceholderIsRefused(final String raw) {
        assertThatThrownBy(() -> TemplatedValue.of(raw))
                .isInstanceOf(InvalidTemplatedValueException.class)
                .hasMessageContaining(raw);
    }

    @Test
    void renderedUsesTheValueThenTheDefaultThenNothing() {
        TemplatedValue value = TemplatedValue.of("{{env}}-{{app:shop}}-{{other}}$1");

        assertThat(value.rendered(Map.of("env", "prod"))).isEqualTo("prod-shop-$1");
        assertThat(value.rendered(Map.of("env", "a", "app", "b"))).isEqualTo("a-b-$1");
        assertThat(TemplatedValue.of("plain").rendered(Map.of())).isEqualTo("plain");
    }
}
