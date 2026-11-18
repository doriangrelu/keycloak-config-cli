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

package io.github.doriangrelu.keycloak.config.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule.Matching;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommonModelTest {

    @Test
    void equalsRuleMatchesTheExactName() {
        ProtectionRule rule = new ProtectionRule(Matching.EQUALS, "admin");

        assertThat(rule.protects("admin")).isTrue();
        assertThat(rule.protects("administrator")).isFalse();
        assertThat(rule.protects(null)).isFalse();
    }

    @Test
    void patternRuleMatchesTheWholeName() {
        ProtectionRule rule = new ProtectionRule(Matching.PATTERN, "app-.*");

        assertThat(rule.protects("app-reader")).isTrue();
        assertThat(rule.protects("my-app-reader")).isFalse();
    }

    @Test
    void invalidPatternIsRejectedWhenTheRuleIsCreated() {
        assertThatThrownBy(() -> new ProtectionRule(Matching.PATTERN, "(")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unmodeledAttributesAreOverriddenKeyByKey() {
        UnmodeledAttributes actual = new UnmodeledAttributes(Map.of("a", "1", "b", "2"));

        UnmodeledAttributes merged = actual.overriddenBy(new UnmodeledAttributes(Map.of("b", "3")));

        assertThat(merged.values()).containsExactlyInAnyOrderEntriesOf(Map.of("a", "1", "b", "3"));
    }

    @Test
    void unmodeledAttributesKeepExplicitNulls() {
        Map<String, Object> values = new HashMap<>();
        values.put("cleared", null);

        assertThat(new UnmodeledAttributes(values).values()).containsKey("cleared");
    }

    @Test
    void unmodeledAttributesOnlyAcceptSimpleJsonValues() {
        assertThat(new UnmodeledAttributes(Map.of("list", List.of("x", 1, true), "map", Map.of("k", 2.5))).isEmpty())
                .isFalse();
        assertThatThrownBy(() -> new UnmodeledAttributes(Map.of("object", new Object())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
