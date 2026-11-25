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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Check;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.DeclarativeRule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Operator;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Severity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadRealmsTest {

    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());

    private final ReadRealms readRealms = new ReadRealms(new FakeRealmDefinitions(DEMO), Rules.none());

    @Test
    void theRealmsAreTheFoldersOfTheRoot() {
        assertThat(this.readRealms.list()).containsExactly("demo");
        assertThat(this.readRealms.open("demo")).isEqualTo(DEMO);
    }

    @Test
    void anUnknownFolderIsReported() {
        assertThatThrownBy(() -> this.readRealms.open("nope"))
                .isInstanceOf(UnknownRealmFolderException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void checkListsFindingsByElementAndDisabledRules() {
        Origin clientOrigin = new Origin("20-clients.yaml", 3);
        Origin roleOrigin = new Origin("30-roles.yaml", 2);
        ClientDefinition client = new ClientDefinition(
                TemplatedValue.of("app"),
                Optional.empty(),
                Optional.empty(),
                ApplicationType.API,
                List.of(),
                List.of(),
                Map.of());
        RoleDefinition role = new RoleDefinition(TemplatedValue.of("reader"), Optional.empty(), Optional.empty());
        RealmDefinition realm = new RealmDefinition(
                "demo",
                Optional.empty(),
                List.of(new Defined<>(client, clientOrigin)),
                List.of(new Defined<>(role, roleOrigin)),
                List.of(),
                Set.of(),
                List.of());
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm);
        definitions.elementKeys.put(
                clientOrigin, Rules.withList(Rules.client(ApplicationType.API), "redirectUris", "https://*"));
        definitions.elementKeys.put(roleOrigin, Rules.keys("name", "reader"));
        definitions.realmKeys = Rules.keys("realm", "demo");
        DeclarativeRule sslRequired = new DeclarativeRule(
                "realm.ssl",
                RuleElement.REALM,
                Optional.empty(),
                List.of(),
                ElementKeys.empty(),
                List.of(new Check(KeyPath.parse("sslRequired"), Operator.EQUALS, List.of("external"))),
                Severity.REQUIRED,
                "SSL is required.");
        RuleBook rules = Rules.disabling("client.described", Rules.NO_WILDCARD, Rules.DESCRIBED, sslRequired);

        RealmCheck check = new ReadRealms(definitions, rules).check("demo");

        assertThat(check.findings()).containsOnlyKeys(clientOrigin);
        assertThat(check.findings().get(clientOrigin))
                .extracting(finding -> finding.ruleId())
                .containsExactly("client.no-wildcard");
        assertThat(check.realmFindings())
                .extracting(finding -> finding.ruleId())
                .containsExactly("realm.ssl");
        assertThat(check.disabled()).extracting(d -> d.id()).containsExactly("client.described");
    }
}
