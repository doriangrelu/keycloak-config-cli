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

package io.github.doriangrelu.keycloak.config.infrastructure.source.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Disablement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Severity;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleFileReaderTest {

    private static final String FILE = "rules.yaml";

    private static Rule only(final List<Rule> rules) {
        assertThat(rules).hasSize(1);
        return rules.get(0);
    }

    private static void refused(final String yaml, final String... fragments) {
        assertThatThrownBy(() -> RuleFileReader.rules(FILE, yaml))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageStartingWith(FILE + ": ")
                .satisfies(e -> assertThat(e.getMessage()).contains(fragments));
    }

    @Test
    void theBaseRulesAreTheTwoExamples() {
        List<Rule> base = new RuleFilesOnDisk(Optional.empty()).base();

        assertThat(base).extracting(Rule::id).containsExactly("client.spa.pkce", "client.redirect-uris.no-wildcard");
        assertThat(base).extracting(Rule::severity).containsOnly(Severity.REQUIRED);
        assertThat(base.get(0).message())
                .isEqualTo("A front-end application must use PKCE with S256: the browser cannot keep a secret.");
        assertThat(base.get(1).message())
                .isEqualTo("A redirect URI '*' lets any site receive the tokens. List the application's URIs.");
    }

    @Test
    void anOrganisationFileAddsRulesAndDisablements() {
        RuleFilesOnDisk files =
                new RuleFilesOnDisk(Optional.of(Path.of("src/test/resources/rules/organisation-rules.yaml")));

        OrganisationRules organisation = files.organisation();

        assertThat(organisation.rules()).extracting(Rule::id).containsExactly("org.client.logout");
        assertThat(organisation.rules().get(0).severity()).isEqualTo(Severity.ADVICE);
        assertThat(organisation.rules().get(0).element()).isEqualTo(RuleElement.CLIENT);
        assertThat(organisation.disablements())
                .containsExactly(new Disablement(
                        "client.spa.pkce",
                        Set.of("legacy-portal"),
                        "Legacy portal without PKCE support, replacement planned in 2027."));
    }

    @Test
    void noOrganisationFileMeansNoOrganisationRules() {
        assertThat(new RuleFilesOnDisk(Optional.empty()).organisation()).isEqualTo(OrganisationRules.none());
    }

    @Test
    void aBooleanDefaultIsReadAsText() {
        String yaml = """
                rules:
                  - id: client.logout
                    element: client
                    when:
                      application-type: web-application
                    defaults:
                      frontchannelLogout: true
                      tokenLifespan: 3600
                      redirectUris: [a, 1]
                    message: "Log out."
                """;

        Rule rule = only(RuleFileReader.rules(FILE, yaml));

        assertThat(rule.defaults().get(KeyPath.parse("frontchannelLogout")))
                .contains(new KeyValue.Scalar(TemplatedValue.of("true")));
        assertThat(rule.defaults().get(KeyPath.parse("tokenLifespan")))
                .contains(new KeyValue.Scalar(TemplatedValue.of("3600")));
        assertThat(rule.defaults().get(KeyPath.parse("redirectUris")))
                .contains(new KeyValue.Listed(List.of(TemplatedValue.of("a"), TemplatedValue.of("1"))));
    }

    @Test
    void aBooleanOperandIsReadAsText() {
        String yaml = """
                rules:
                  - id: client.example
                    element: client
                    checks:
                      - key: publicClient
                        equals: true
                    message: "Example."
                """;

        Rule rule = only(RuleFileReader.rules(FILE, yaml));

        assertThat(rule.id()).isEqualTo("client.example");
        assertThat(rule.severity()).isEqualTo(Severity.REQUIRED);
    }

    @Test
    void theWhenSectionHoldsTheApplicationTypeAndKeyConditions() {
        String yaml = """
                rules:
                  - id: client.spa.example
                    element: client
                    when:
                      application-type: spa
                      checks:
                        - key: client
                          absent: true
                    checks:
                      - key: webOrigins
                        not-one-of: ["*"]
                    message: "Example."
                """;

        assertThat(only(RuleFileReader.rules(FILE, yaml)).id()).isEqualTo("client.spa.example");
    }

    @Test
    void anUnknownFieldIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    chekcs:
                      - key: publicClient
                        equals: true
                    message: "x"
                """, "rule 'client.x'", "chekcs");
    }

    @Test
    void anUnknownOperatorIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    checks:
                      - key: publicClient
                        is: true
                    message: "x"
                """, "rule 'client.x'", "is");
    }

    @Test
    void twoOperatorsInOneCheckAreRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    checks:
                      - key: publicClient
                        equals: true
                        present: true
                    message: "x"
                """, "rule 'client.x'", "one operator");
    }

    @Test
    void anUnknownElementIsRefused() {
        refused("""
                rules:
                  - id: user.x
                    element: user
                    checks:
                      - key: enabled
                        equals: true
                    message: "x"
                """, "rule 'user.x'", "user");
    }

    @Test
    void anUnknownApplicationTypeIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    when:
                      application-type: frontend
                    checks:
                      - key: publicClient
                        equals: true
                    message: "x"
                """, "rule 'client.x'", "frontend");
    }

    @Test
    void anApplicationTypeOnARoleIsRefused() {
        refused("""
                rules:
                  - id: role.x
                    element: role
                    when:
                      application-type: spa
                    checks:
                      - key: description
                        present: true
                    message: "x"
                """, "rule 'role.x'", "application type");
    }

    @Test
    void equalsWithAListIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    checks:
                      - key: attributes.foo
                        equals: [a, b]
                    message: "x"
                """, "rule 'client.x'", "equals");
    }

    @Test
    void equalsOnAKnownListKeyIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    checks:
                      - key: redirectUris
                        equals: "https://a"
                    message: "x"
                """, "rule 'client.x'", "redirectUris", "equals");
    }

    @Test
    void aMissingMessageIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    checks:
                      - key: publicClient
                        equals: true
                """, "rule 'client.x'", "message");
    }

    @Test
    void aRuleWithoutDefaultsNorChecksIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    message: "x"
                """, "rule 'client.x'", "defaults or checks");
    }

    @Test
    void anInvalidSeverityIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    severity: blocker
                    checks:
                      - key: publicClient
                        equals: true
                    message: "x"
                """, "rule 'client.x'", "blocker");
    }

    @Test
    void anInvalidRegularExpressionIsRefused() {
        refused("""
                rules:
                  - id: client.x
                    element: client
                    checks:
                      - key: redirectUris
                        matches: "("
                    message: "x"
                """, "rule 'client.x'", "regular expression");
    }

    @Test
    void twoRulesWithTheSameDefaultKeyAndADifferentValueAreRefused() {
        refused("""
                rules:
                  - id: client.a
                    element: client
                    defaults:
                      accessTokenLifespan: 300
                    message: "a"
                  - id: client.b
                    element: client
                    defaults:
                      accessTokenLifespan: 600
                    message: "b"
                """, "rule 'client.b'", "client.a", "accessTokenLifespan");
    }

    @Test
    void twoRulesWithTheSameDefaultValueOrADifferentWhenAreAccepted() {
        String yaml = """
                rules:
                  - id: client.a
                    element: client
                    defaults:
                      accessTokenLifespan: 300
                    message: "a"
                  - id: client.b
                    element: client
                    defaults:
                      accessTokenLifespan: 300
                    message: "b"
                  - id: client.c
                    element: client
                    when:
                      application-type: spa
                    defaults:
                      accessTokenLifespan: 600
                    message: "c"
                """;

        assertThat(RuleFileReader.rules(FILE, yaml)).hasSize(3);
    }

    @Test
    void aMalformedYamlIsRefused() {
        refused("rules: [unclosed", "YAML");
    }

    @Test
    void aFileThatIsNotAnObjectIsRefused() {
        refused("- just\n- a list\n", "object");
    }

    @Test
    void anUnknownTopLevelFieldIsRefused() {
        refused("rule: []\n", "rule");
    }

    @Test
    void aDisablementWithoutReasonIsRefused() {
        assertThatThrownBy(() -> RuleFileReader.organisation(FILE, """
                        disable:
                          - id: client.spa.pkce
                        """))
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageStartingWith(FILE + ": ")
                .hasMessageContaining("client.spa.pkce")
                .hasMessageContaining("reason");
    }

    @Test
    void aMissingOrganisationFileIsRefused() {
        RuleFilesOnDisk files = new RuleFilesOnDisk(Optional.of(Path.of("src/test/resources/rules/missing.yaml")));

        assertThatThrownBy(files::organisation)
                .isInstanceOf(InvalidRulesException.class)
                .hasMessageContaining("missing.yaml");
    }
}
