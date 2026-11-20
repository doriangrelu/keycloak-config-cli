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

package io.github.doriangrelu.keycloak.config.domain.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.common.DomainException;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ProtectionRule.Matching;
import org.junit.jupiter.api.Test;

class ProtectionRuleTest {

    @Test
    void anEqualsRuleMatchesTheExactName() {
        ProtectionRule rule = new ProtectionRule(Matching.EQUALS, "admin");

        assertThat(rule.protects(ResourceType.REALM_ROLE, "admin")).isTrue();
        assertThat(rule.protects(ResourceType.REALM_ROLE, "administrator")).isFalse();
        assertThat(rule.protects(ResourceType.REALM_ROLE, null)).isFalse();
    }

    @Test
    void aPatternRuleMatchesTheWholeName() {
        ProtectionRule rule = new ProtectionRule(Matching.PATTERN, "app-.*");

        assertThat(rule.protects(ResourceType.CLIENT, "app-reader")).isTrue();
        assertThat(rule.protects(ResourceType.CLIENT, "my-app-reader")).isFalse();
    }

    @Test
    void aRuleWithoutTypeProtectsEveryType() {
        ProtectionRule rule = new ProtectionRule(Matching.EQUALS, "legacy");

        assertThat(rule.protects(ResourceType.CLIENT, "legacy")).isTrue();
        assertThat(rule.protects(ResourceType.GROUP, "legacy")).isTrue();
        assertThat(rule.type()).isEmpty();
    }

    @Test
    void aRuleWithATypeOnlyProtectsThatType() {
        ProtectionRule rule = new ProtectionRule(Matching.PATTERN, "legacy-.*", ResourceType.CLIENT);

        assertThat(rule.protects(ResourceType.CLIENT, "legacy-app")).isTrue();
        assertThat(rule.protects(ResourceType.CLIENT_SCOPE, "legacy-app")).isFalse();
        assertThat(rule.type()).contains(ResourceType.CLIENT);
    }

    @Test
    void theMatchingIsEqualsByDefault() {
        ProtectionRule rule = new ProtectionRule(null, "app-.*");

        assertThat(rule.matching()).isEqualTo(Matching.EQUALS);
        assertThat(rule.protects(ResourceType.CLIENT, "app-.*")).isTrue();
        assertThat(rule.protects(ResourceType.CLIENT, "app-reader")).isFalse();
    }

    @Test
    void anInvalidPatternIsAConfigurationError() {
        assertThatThrownBy(() -> new ProtectionRule(Matching.PATTERN, "team-("))
                .isInstanceOf(InvalidProtectionRuleException.class)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("'team-('");
    }

    @Test
    void aRuleWithoutMatcherIsAConfigurationError() {
        assertThatThrownBy(() -> new ProtectionRule(Matching.EQUALS, null))
                .isInstanceOf(InvalidProtectionRuleException.class);
    }

    @Test
    void rulesAreValues() {
        assertThat(new ProtectionRule(Matching.PATTERN, "a.*", ResourceType.GROUP))
                .isEqualTo(new ProtectionRule(Matching.PATTERN, "a.*", ResourceType.GROUP))
                .hasSameHashCodeAs(new ProtectionRule(Matching.PATTERN, "a.*", ResourceType.GROUP))
                .isNotEqualTo(new ProtectionRule(Matching.PATTERN, "a.*"));
    }
}
