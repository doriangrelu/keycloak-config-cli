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

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private static final RealmContext CONTEXT = new RealmContext(null, Optional.empty(), BuiltIns.none());
    private static final KeyPath URIS = KeyPath.parse("redirectUris");
    private static final KeyPath PKCE = KeyPath.parse("attributes.\"pkce.code.challenge.method\"");
    private static final KeyPath CLIENT_ID = KeyPath.parse("clientId");

    private static KeyValue scalar(final String text) {
        return new KeyValue.Scalar(TemplatedValue.of(text));
    }

    private static DeclarativeRule noWildcard(final String id, final Severity severity) {
        return new DeclarativeRule(
                id,
                RuleElement.CLIENT,
                Optional.empty(),
                List.of(),
                ElementKeys.empty(),
                List.of(new Check(URIS, Operator.NONE_MATCHES, List.of("^\\*$"))),
                severity,
                "No wildcard.");
    }

    private static DeclarativeRule pkceDefault(final String id, final String value) {
        return new DeclarativeRule(
                id,
                RuleElement.CLIENT,
                Optional.empty(),
                List.of(),
                new ElementKeys(Map.of(PKCE, scalar(value))),
                List.of(),
                Severity.ADVICE,
                "Default.");
    }

    private static RuleEngine engine(final Rule... rules) {
        return new RuleEngine(RuleSet.of(List.of(rules), List.of(), OrganisationRules.none()));
    }

    private static ElementKeys uris(final String uri) {
        return new ElementKeys(
                Map.of(CLIENT_ID, scalar("app"), URIS, new KeyValue.Listed(List.of(TemplatedValue.of(uri)))));
    }

    @Test
    void aNewElementIsBlockedByAnyRequiredFailure() {
        Verdict verdict = engine(noWildcard("r", Severity.REQUIRED))
                .check(RuleElement.CLIENT, Optional.empty(), uris("*"), CONTEXT);

        assertThat(verdict.blocking()).extracting(Finding::ruleId).containsExactly("r");
        assertThat(verdict.allowsWriting()).isFalse();
    }

    @Test
    void aFailureAlreadyThereIsAReminder() {
        Verdict verdict = engine(noWildcard("r", Severity.REQUIRED))
                .check(RuleElement.CLIENT, Optional.of(uris("*")), uris("*"), CONTEXT);

        assertThat(verdict.reminders()).extracting(Finding::ruleId).containsExactly("r");
        assertThat(verdict.allowsWriting()).isTrue();
    }

    @Test
    void aFailureTheEditIntroducesBlocks() {
        Verdict verdict = engine(noWildcard("r", Severity.REQUIRED))
                .check(RuleElement.CLIENT, Optional.of(uris("https://a")), uris("*"), CONTEXT);

        assertThat(verdict.blocking()).hasSize(1);
        assertThat(verdict.reminders()).isEmpty();
    }

    @Test
    void anAdviceNeverBlocks() {
        Verdict verdict = engine(noWildcard("r", Severity.ADVICE))
                .check(RuleElement.CLIENT, Optional.empty(), uris("*"), CONTEXT);

        assertThat(verdict.advice()).hasSize(1);
        assertThat(verdict.allowsWriting()).isTrue();
    }

    @Test
    void anUnverifiableValueIsListedApart() {
        Verdict verdict = engine(noWildcard("r", Severity.REQUIRED))
                .check(RuleElement.CLIENT, Optional.empty(), uris("{{uri}}"), CONTEXT);

        assertThat(verdict.unverifiable()).hasSize(1);
        assertThat(verdict.unverifiable().get(0).outcome()).isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(verdict.allowsWriting()).isTrue();
    }

    @Test
    void aDisabledRuleIsSkipped() {
        OrganisationRules organisation =
                new OrganisationRules(List.of(), List.of(new Disablement("r", Set.of("app"), "Legacy")));
        RuleEngine engine =
                new RuleEngine(RuleSet.of(List.of(noWildcard("r", Severity.REQUIRED)), List.of(), organisation));

        Verdict verdict = engine.check(RuleElement.CLIENT, Optional.empty(), uris("*"), CONTEXT);

        assertThat(verdict.blocking()).isEmpty();
        assertThat(engine.report(RuleElement.CLIENT, uris("*"), CONTEXT)).isEmpty();
    }

    @Test
    void defaultsSkipKeysAlreadyThere() {
        RuleEngine engine = engine(pkceDefault("d", "S256"));

        assertThat(engine.defaults(RuleElement.CLIENT, ElementKeys.empty()).values())
                .containsOnlyKeys(PKCE);
        assertThat(engine.defaults(RuleElement.CLIENT, new ElementKeys(Map.of(PKCE, scalar("plain"))))
                        .values())
                .isEmpty();
    }

    @Test
    void theOrganisationDefaultWins() {
        RuleSet set = RuleSet.of(
                List.of(pkceDefault("base", "S256")),
                List.of(),
                new OrganisationRules(List.of(pkceDefault("org", "plain")), List.of()));

        ElementKeys defaults = new RuleEngine(set).defaults(RuleElement.CLIENT, ElementKeys.empty());

        assertThat(defaults.get(PKCE)).contains(scalar("plain"));
    }

    @Test
    void reportListsRequiredBeforeAdviceAndSkipsUnverifiable() {
        RuleEngine engine = engine(noWildcard("advice", Severity.ADVICE), noWildcard("required", Severity.REQUIRED));

        assertThat(engine.report(RuleElement.CLIENT, uris("*"), CONTEXT))
                .extracting(Finding::ruleId)
                .containsExactly("required", "advice");
        assertThat(engine.report(RuleElement.CLIENT, uris("{{x}}"), CONTEXT)).isEmpty();
    }
}
