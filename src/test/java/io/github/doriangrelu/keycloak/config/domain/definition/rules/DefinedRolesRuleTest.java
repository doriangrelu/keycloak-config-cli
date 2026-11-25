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

import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.client;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.keys;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.listed;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.realm;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.role;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefinedRolesRuleTest {

    private static final BuiltIns BUILT_INS = new BuiltIns(
            Set.of("realm-management"), Set.of("offline_access"), Map.of("realm-management", Set.of("manage-users")));

    private final DefinedRolesRule rule = new DefinedRolesRule();

    private Outcome check(final ElementKeys keys) {
        var realm =
                realm(List.of(client("app", 1)), List.of(role("editor", null, 1), role("reader", "app", 2)), List.of());
        return this.rule.check(keys, new RealmContext(realm, Optional.empty(), BUILT_INS));
    }

    @Test
    void identity() {
        assertThat(this.rule.id()).isEqualTo("group.defined-roles");
        assertThat(this.rule.element()).isEqualTo(RuleElement.GROUP);
        assertThat(this.rule.severity()).isEqualTo(Severity.REQUIRED);
        assertThat(this.rule.appliesTo(ElementKeys.empty())).isTrue();
        assertThat(this.rule.defaults().values()).isEmpty();
        assertThat(this.rule.message()).isNotBlank();
    }

    @Test
    void built_in_realm_role_passes() {
        assertThat(check(keys("realmRoles", listed("offline_access")))).isEqualTo(Outcome.PASSED);
    }

    @Test
    void role_defined_in_realm_passes() {
        assertThat(check(keys("realmRoles", listed("editor", "offline_access"))))
                .isEqualTo(Outcome.PASSED);
    }

    @Test
    void unknown_realm_role_fails() {
        assertThat(check(keys("realmRoles", listed("unknown-role")))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void built_in_client_role_passes() {
        assertThat(check(keys("clientRoles.\"realm-management\"", listed("manage-users"))))
                .isEqualTo(Outcome.PASSED);
    }

    @Test
    void client_role_defined_in_realm_passes() {
        assertThat(check(keys("clientRoles.\"app\"", listed("reader")))).isEqualTo(Outcome.PASSED);
    }

    @Test
    void unknown_client_role_or_client_fails() {
        assertThat(check(keys("clientRoles.\"app\"", listed("editor")))).isEqualTo(Outcome.FAILED);
        assertThat(check(keys("clientRoles.\"ghost\"", listed("reader")))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void variable_name_is_unverifiable_unless_another_role_fails() {
        assertThat(check(keys("realmRoles", listed("{{role}}")))).isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(check(keys("realmRoles", listed("{{role}}", "nope")))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void default_role_of_the_realm_is_built_in() {
        var context = new RealmContext(RealmRulesFixtures.namedRealm("Demo"), Optional.empty(), BUILT_INS);
        assertThat(this.rule.check(keys("realmRoles", listed("default-roles-demo")), context))
                .isEqualTo(Outcome.PASSED);
        assertThat(this.rule.check(keys("realmRoles", listed("default-roles-other")), context))
                .isEqualTo(Outcome.FAILED);
    }

    @Test
    void default_role_of_a_templated_or_unnamed_realm_is_unverifiable() {
        var templated = new RealmContext(RealmRulesFixtures.namedRealm("{{env}}-demo"), Optional.empty(), BUILT_INS);
        assertThat(this.rule.check(keys("realmRoles", listed("default-roles-dev-demo")), templated))
                .isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(this.rule.check(keys("realmRoles", listed("unknown-role")), templated))
                .isEqualTo(Outcome.FAILED);
        assertThat(check(keys("realmRoles", listed("default-roles-demo")))).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void group_without_roles_passes() {
        assertThat(check(ElementKeys.empty())).isEqualTo(Outcome.PASSED);
    }
}
