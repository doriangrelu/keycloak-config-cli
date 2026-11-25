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

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientFlows;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeclarativeRuleTest {

    private static final RealmContext CONTEXT = new RealmContext(null, Optional.empty(), BuiltIns.none());

    private static final KeyPath PKCE_KEY = KeyPath.parse("attributes.\"pkce.code.challenge.method\"");

    private static final DeclarativeRule PKCE = new DeclarativeRule(
            "client.spa.pkce",
            RuleElement.CLIENT,
            Optional.of(ApplicationType.SPA),
            List.of(),
            new ElementKeys(Map.of(PKCE_KEY, scalar("S256"))),
            List.of(new Check(PKCE_KEY, Operator.EQUALS, List.of("S256"))),
            Severity.REQUIRED,
            "A front-end application must use PKCE with S256: the browser cannot keep a secret.");

    private static final DeclarativeRule NO_WILDCARD = new DeclarativeRule(
            "client.redirect-uris.no-wildcard",
            RuleElement.CLIENT,
            Optional.empty(),
            List.of(),
            ElementKeys.empty(),
            List.of(new Check(KeyPath.parse("redirectUris"), Operator.NONE_MATCHES, List.of("^\\*$"))),
            Severity.REQUIRED,
            "A redirect URI '*' lets any site receive the tokens. List the URIs of the application.");

    private static KeyValue scalar(final String text) {
        return new KeyValue.Scalar(TemplatedValue.of(text));
    }

    private static Map<KeyPath, KeyValue> flows(final ClientFlows flows) {
        Map<KeyPath, KeyValue> map = new LinkedHashMap<>();
        map.put(KeyPath.parse("publicClient"), scalar(String.valueOf(flows.publicClient())));
        map.put(KeyPath.parse("standardFlowEnabled"), scalar(String.valueOf(flows.standardFlow())));
        map.put(KeyPath.parse("implicitFlowEnabled"), scalar(String.valueOf(flows.implicitFlow())));
        map.put(KeyPath.parse("directAccessGrantsEnabled"), scalar(String.valueOf(flows.directAccessGrants())));
        map.put(KeyPath.parse("serviceAccountsEnabled"), scalar(String.valueOf(flows.serviceAccounts())));
        return map;
    }

    private static ElementKeys view(final ClientFlows flows, final Map<KeyPath, KeyValue> more) {
        Map<KeyPath, KeyValue> map = flows(flows);
        map.putAll(more);
        return new ElementKeys(map);
    }

    private static ElementKeys spa(final Map<KeyPath, KeyValue> more) {
        return view(ClientFlows.of(ApplicationType.SPA), more);
    }

    @Test
    void pkceAppliesToASpaOnly() {
        assertThat(PKCE.appliesTo(spa(Map.of()))).isTrue();
        assertThat(PKCE.appliesTo(view(ClientFlows.of(ApplicationType.WEB_APPLICATION), Map.of())))
                .isFalse();
        assertThat(PKCE.appliesTo(view(new ClientFlows(true, true, true, true, true), Map.of())))
                .isFalse();
    }

    @Test
    void aTemplatedFlowDoesNotApplyWhenAnApplicationTypeIsRequired() {
        Map<KeyPath, KeyValue> more = Map.of(KeyPath.parse("publicClient"), scalar("{{public}}"));
        assertThat(PKCE.appliesTo(spa(more))).isFalse();
    }

    @Test
    void aMissingFlowDoesNotApplyWhenAnApplicationTypeIsRequired() {
        assertThat(PKCE.appliesTo(ElementKeys.empty())).isFalse();
    }

    @Test
    void aRuleWithoutApplicationTypeAppliesWithoutFlows() {
        assertThat(NO_WILDCARD.appliesTo(ElementKeys.empty())).isTrue();
    }

    @Test
    void aSpaWithoutPkceFails() {
        assertThat(PKCE.check(spa(Map.of()), CONTEXT)).isEqualTo(Outcome.FAILED);
    }

    @Test
    void aSpaWithS256Passes() {
        assertThat(PKCE.check(spa(Map.of(PKCE_KEY, scalar("S256"))), CONTEXT)).isEqualTo(Outcome.PASSED);
    }

    @Test
    void aTemplatedRedirectUriIsUnverifiable() {
        ElementKeys keys = new ElementKeys(
                Map.of(KeyPath.parse("redirectUris"), new KeyValue.Listed(List.of(TemplatedValue.of("{{appUrl}}/*")))));
        assertThat(NO_WILDCARD.check(keys, CONTEXT)).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void aWildcardRedirectUriFails() {
        ElementKeys keys = new ElementKeys(
                Map.of(KeyPath.parse("redirectUris"), new KeyValue.Listed(List.of(TemplatedValue.of("*")))));
        assertThat(NO_WILDCARD.check(keys, CONTEXT)).isEqualTo(Outcome.FAILED);
    }

    @Test
    void aFailedCheckWinsOverAnUnverifiableOne() {
        DeclarativeRule rule = new DeclarativeRule(
                "client.two",
                RuleElement.CLIENT,
                Optional.empty(),
                List.of(),
                ElementKeys.empty(),
                List.of(
                        new Check(KeyPath.parse("a"), Operator.EQUALS, List.of("x")),
                        new Check(KeyPath.parse("b"), Operator.EQUALS, List.of("y"))),
                Severity.ADVICE,
                "m");
        ElementKeys keys = new ElementKeys(Map.of(KeyPath.parse("a"), scalar("{{v}}")));
        assertThat(rule.check(keys, CONTEXT)).isEqualTo(Outcome.FAILED);
    }

    @Test
    void aConditionThatIsNotPassedDoesNotApply() {
        Check condition = new Check(KeyPath.parse("protocol"), Operator.EQUALS, List.of("saml"));
        DeclarativeRule rule = new DeclarativeRule(
                "client.saml",
                RuleElement.CLIENT,
                Optional.empty(),
                List.of(condition),
                ElementKeys.empty(),
                List.of(new Check(KeyPath.parse("x"), Operator.PRESENT, List.of())),
                Severity.ADVICE,
                "m");
        assertThat(rule.appliesTo(new ElementKeys(Map.of(KeyPath.parse("protocol"), scalar("saml")))))
                .isTrue();
        assertThat(rule.appliesTo(new ElementKeys(Map.of(KeyPath.parse("protocol"), scalar("openid-connect")))))
                .isFalse();
        assertThat(rule.appliesTo(new ElementKeys(Map.of(KeyPath.parse("protocol"), scalar("{{p}}")))))
                .isFalse();
        assertThat(rule.appliesTo(ElementKeys.empty())).isFalse();
    }

    @Test
    void aRuleWithoutDefaultsNorChecksIsRefused() {
        assertThatThrownBy(() -> new DeclarativeRule(
                        "client.empty",
                        RuleElement.CLIENT,
                        Optional.empty(),
                        List.of(),
                        ElementKeys.empty(),
                        List.of(),
                        Severity.ADVICE,
                        "m"))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageContaining("client.empty");
    }

    @Test
    void anApplicationTypeOutsideAClientIsRefused() {
        assertThatThrownBy(() -> new DeclarativeRule(
                        "role.typed",
                        RuleElement.ROLE,
                        Optional.of(ApplicationType.SPA),
                        List.of(),
                        ElementKeys.empty(),
                        List.of(new Check(KeyPath.parse("x"), Operator.PRESENT, List.of())),
                        Severity.ADVICE,
                        "m"))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageContaining("role.typed");
    }

    @Test
    void aBlankIdOrMessageIsRefused() {
        Check check = new Check(KeyPath.parse("x"), Operator.PRESENT, List.of());
        assertThatThrownBy(() -> new DeclarativeRule(
                        " ",
                        RuleElement.CLIENT,
                        Optional.empty(),
                        List.of(),
                        ElementKeys.empty(),
                        List.of(check),
                        Severity.ADVICE,
                        "m"))
                .isInstanceOf(InvalidRulesException.class);
        assertThatThrownBy(() -> new DeclarativeRule(
                        "client.nomessage",
                        RuleElement.CLIENT,
                        Optional.empty(),
                        List.of(),
                        ElementKeys.empty(),
                        List.of(check),
                        Severity.ADVICE,
                        ""))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageContaining("client.nomessage");
    }
}
