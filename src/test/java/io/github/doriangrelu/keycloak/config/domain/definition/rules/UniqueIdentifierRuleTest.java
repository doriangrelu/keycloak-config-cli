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
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.group;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.keys;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.realm;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.role;
import static io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmRulesFixtures.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UniqueIdentifierRuleTest {

    private static final Origin FIRST_CLIENT = new Origin("clients.yaml", 1);

    private static RealmContext context(final RealmDefinition realm, final Origin self) {
        return new RealmContext(realm, Optional.ofNullable(self), BuiltIns.none());
    }

    @Test
    void ids_are_one_per_element() {
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT).id()).isEqualTo("client.unique-identifier");
        assertThat(new UniqueIdentifierRule(RuleElement.ROLE).id()).isEqualTo("role.unique-identifier");
        assertThat(new UniqueIdentifierRule(RuleElement.GROUP).id()).isEqualTo("group.unique-identifier");
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT).severity()).isEqualTo(Severity.REQUIRED);
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT).appliesTo(ElementKeys.empty()))
                .isTrue();
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT).defaults().values())
                .isEmpty();
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT).message()).isNotBlank();
    }

    @Test
    void realm_element_is_refused() {
        assertThatThrownBy(() -> new UniqueIdentifierRule(RuleElement.REALM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicated_client_id_fails() {
        var realm = realm(List.of(client("app", 1), client("app", 5)), List.of(), List.of());
        Outcome outcome = new UniqueIdentifierRule(RuleElement.CLIENT)
                .check(keys("clientId", scalar("app")), context(realm, FIRST_CLIENT));
        assertThat(outcome).isEqualTo(Outcome.FAILED);
    }

    @Test
    void element_checked_against_itself_passes() {
        var realm = realm(List.of(client("app", 1), client("other", 5)), List.of(), List.of());
        Outcome outcome = new UniqueIdentifierRule(RuleElement.CLIENT)
                .check(keys("clientId", scalar("app")), context(realm, FIRST_CLIENT));
        assertThat(outcome).isEqualTo(Outcome.PASSED);
    }

    @Test
    void new_element_without_origin_clashes_with_any_existing() {
        var realm = realm(List.of(client("app", 1)), List.of(), List.of());
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT)
                        .check(keys("clientId", scalar("app")), context(realm, null)))
                .isEqualTo(Outcome.FAILED);
    }

    @Test
    void variable_identifier_is_unverifiable() {
        var realm = realm(List.of(client("app", 1), client("app", 5)), List.of(), List.of());
        assertThat(new UniqueIdentifierRule(RuleElement.CLIENT)
                        .check(keys("clientId", scalar("{{id}}")), context(realm, FIRST_CLIENT)))
                .isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void realm_role_name_must_be_unique_among_realm_roles_only() {
        UniqueIdentifierRule rule = new UniqueIdentifierRule(RuleElement.ROLE);
        Origin self = new Origin("roles.yaml", 1);
        var realm = realm(List.of(), List.of(role("admin", null, 1), role("admin", "app", 2)), List.of());
        assertThat(rule.check(keys("name", scalar("admin")), context(realm, self)))
                .isEqualTo(Outcome.PASSED);
        var duplicated = realm(List.of(), List.of(role("admin", null, 1), role("admin", null, 3)), List.of());
        assertThat(rule.check(keys("name", scalar("admin")), context(duplicated, self)))
                .isEqualTo(Outcome.FAILED);
    }

    @Test
    void client_role_name_must_be_unique_within_its_client() {
        UniqueIdentifierRule rule = new UniqueIdentifierRule(RuleElement.ROLE);
        Origin self = new Origin("roles.yaml", 1);
        var same = realm(List.of(), List.of(role("admin", "app", 1), role("admin", "app", 3)), List.of());
        assertThat(rule.check(keys("name", scalar("admin"), "client", scalar("app")), context(same, self)))
                .isEqualTo(Outcome.FAILED);
        var other = realm(List.of(), List.of(role("admin", "app", 1), role("admin", "other", 3)), List.of());
        assertThat(rule.check(keys("name", scalar("admin"), "client", scalar("app")), context(other, self)))
                .isEqualTo(Outcome.PASSED);
    }

    @Test
    void group_path_must_be_unique() {
        UniqueIdentifierRule rule = new UniqueIdentifierRule(RuleElement.GROUP);
        Origin self = new Origin("groups.yaml", 1);
        var realm = realm(List.of(), List.of(), List.of(group("/a/b", 1), group("/a/b", 4), group("/a", 7)));
        assertThat(rule.check(keys("path", scalar("/a/b")), context(realm, self)))
                .isEqualTo(Outcome.FAILED);
        assertThat(rule.check(keys("path", scalar("/a")), context(realm, new Origin("groups.yaml", 7))))
                .isEqualTo(Outcome.PASSED);
    }
}
