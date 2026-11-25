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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleSetTest {

    private static final KeyPath KEY = KeyPath.parse("attributes.\"pkce.code.challenge.method\"");

    private static Rule rule(final String id) {
        return new DeclarativeRule(
                id,
                RuleElement.CLIENT,
                Optional.empty(),
                List.of(),
                new ElementKeys(Map.of(KEY, new KeyValue.Scalar(TemplatedValue.of("S256")))),
                List.of(),
                Severity.REQUIRED,
                "A message.");
    }

    @Test
    void aDuplicateIdIsRefused() {
        OrganisationRules organisation = new OrganisationRules(List.of(rule("client.a")), List.of());

        assertThatThrownBy(() -> RuleSet.of(List.of(rule("client.a")), List.of(), organisation))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageContaining("client.a");
    }

    @Test
    void anUnknownDisabledRuleIsRefused() {
        OrganisationRules organisation =
                new OrganisationRules(List.of(), List.of(new Disablement("client.spa.pkse", Set.of(), "Legacy")));

        assertThatThrownBy(() -> RuleSet.of(List.of(rule("client.spa.pkce")), List.of(), organisation))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageContaining("Unknown rule 'client.spa.pkse' in disable");
    }

    @Test
    void aDisablementWithoutReasonIsRefused() {
        assertThatThrownBy(() -> new Disablement("client.a", Set.of(), " ")).isInstanceOf(InvalidRulesException.class);
    }

    @Test
    void aDisablementForOneClientKeepsTheRuleForTheOthers() {
        OrganisationRules organisation =
                new OrganisationRules(List.of(), List.of(new Disablement("client.a", Set.of("legacy"), "Legacy")));
        RuleSet set = RuleSet.of(List.of(rule("client.a")), List.of(), organisation);

        assertThat(set.isDisabled("client.a", "legacy")).isTrue();
        assertThat(set.isDisabled("client.a", "other")).isFalse();
    }

    @Test
    void aDisablementWithoutElementsCoversAll() {
        OrganisationRules organisation =
                new OrganisationRules(List.of(), List.of(new Disablement("client.a", Set.of(), "Legacy")));
        RuleSet set = RuleSet.of(List.of(rule("client.a")), List.of(), organisation);

        assertThat(set.isDisabled("client.a", "any")).isTrue();
        assertThat(set.isDisabled("client.a", (String) null)).isTrue();
    }

    @Test
    void rulesComeInTheOrderBaseJavaOrganisation() {
        OrganisationRules organisation = new OrganisationRules(List.of(rule("c")), List.of());
        RuleSet set = RuleSet.of(List.of(rule("a")), List.of(rule("b")), organisation);

        assertThat(set.rules()).extracting(Rule::id).containsExactly("a", "b", "c");
    }
}
