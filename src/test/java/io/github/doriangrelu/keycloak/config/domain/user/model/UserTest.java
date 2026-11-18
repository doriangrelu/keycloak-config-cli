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

package io.github.doriangrelu.keycloak.config.domain.user.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final UserMergeRules NOTHING_KEPT = new UserMergeRules(Set.of(), Set.of(), false);

    private static User user(
            String username, Set<String> realmRoles, Map<String, Set<String>> clientRoles, Set<String> groups) {
        return new User(
                username, null, null, null, null, null, null, null, null, realmRoles, clientRoles, groups, null);
    }

    @Test
    void groupNamesAreReadAsPaths() {
        assertThat(user("u", null, null, Set.of("a", "/b/c")).groups()).containsExactlyInAnyOrder("/a", "/b/c");
    }

    @Test
    void undeclaredMappingsAndGroupsAreRemoved() {
        User current = user("u", Set.of("reader"), Map.of("app", Set.of("viewer")), Set.of("/a"));

        User merged = current.mergedWith(user("u", null, null, null), NOTHING_KEPT);

        assertThat(merged.realmRoles()).isEmpty();
        assertThat(merged.clientRoles()).isEmpty();
        assertThat(merged.groups()).isEmpty();
    }

    @Test
    void keptRealmRolesAndKeycloakClientsSurviveUnlessDeclared() {
        User current = user(
                "u",
                Set.of("default-roles-test", "reader"),
                Map.of("account", Set.of("view-profile"), "app", Set.of("viewer")),
                Set.of());
        UserMergeRules rules = new UserMergeRules(Set.of("default-roles-test"), Set.of("account"), false);

        User merged = current.mergedWith(user("u", Set.of("writer"), Map.of("app", Set.of("editor")), null), rules);

        assertThat(merged.realmRoles()).containsExactlyInAnyOrder("writer", "default-roles-test");
        assertThat(merged.clientRoles())
                .containsExactlyInAnyOrderEntriesOf(Map.of("app", Set.of("editor"), "account", Set.of("view-profile")));

        User declaringAccount =
                current.mergedWith(user("u", null, Map.of("account", Set.of("manage-account")), null), rules);
        assertThat(declaringAccount.clientRoles())
                .containsExactlyInAnyOrderEntriesOf(Map.of("account", Set.of("manage-account")));

        User emptyingAccount = current.mergedWith(user("u", null, Map.of("account", Set.of()), null), rules);
        assertThat(emptyingAccount.clientRoles()).isEmpty();
    }

    @Test
    void initialCredentialsAreOnlySetOnCreation() {
        Credential initial = new Credential(Credential.PASSWORD, Credential.INITIAL, null);
        Credential password = new Credential(Credential.PASSWORD, null, null);
        User current = user("u", Set.of(), Map.of(), Set.of());

        User onlyInitial = current.mergedWith(
                new User("u", null, null, null, null, null, null, null, List.of(initial), null, null, null, null),
                NOTHING_KEPT);
        User both = current.mergedWith(
                new User(
                        "u",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(initial, password),
                        null,
                        null,
                        null,
                        null),
                NOTHING_KEPT);

        assertThat(onlyInitial.credentials()).isNull();
        assertThat(onlyInitial).isEqualTo(current);
        assertThat(both.credentials()).containsExactly(password);
        assertThat(both.withoutPassword().credentials()).isNull();
    }

    @Test
    void federatedAttributesAreKeptWhenAsked() {
        User current = new User(
                "u",
                null,
                null,
                null,
                null,
                "ldap",
                null,
                Map.of("a", List.of("1")),
                null,
                Set.of(),
                Map.of(),
                Set.of(),
                null);
        User desired = new User(
                "u", null, null, null, null, null, null, Map.of("b", List.of("2")), null, null, null, null, null);

        assertThat(current.mergedWith(desired, new UserMergeRules(Set.of(), Set.of(), true))
                        .attributes())
                .containsOnlyKeys("a");
        assertThat(current.mergedWith(desired, NOTHING_KEPT).attributes()).containsOnlyKeys("b");
    }

    @Test
    void aUserFoundUnderAnotherUsernameIsRenamed() {
        User found = user("jake", Set.of(), Map.of(), Set.of());

        User matched = found.matchedTo("john");

        assertThat(matched.username()).isEqualTo("john");
        assertThat(matched.previousUsername()).isEqualTo("jake");
        assertThat(matched.mergedWith(user("john", null, null, null), NOTHING_KEPT))
                .isNotEqualTo(matched);
        assertThat(found.matchedTo("JAKE").previousUsername()).isNull();
    }
}
