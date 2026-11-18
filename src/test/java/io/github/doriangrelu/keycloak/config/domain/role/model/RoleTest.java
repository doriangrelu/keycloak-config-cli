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

package io.github.doriangrelu.keycloak.config.domain.role.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoleTest {

    private static Role role(String name, String description, Map<String, List<String>> attributes) {
        return new Role(RoleKey.realm(name), description, attributes, UnmodeledAttributes.none());
    }

    @Test
    void unspecifiedFieldsKeepTheirValueWhenMerged() {
        Role current = role("admin", "Administrators", Map.of("level", List.of("high")));

        assertThat(current.mergedWith(role("admin", null, null))).isEqualTo(current);
    }

    @Test
    void specifiedFieldsReplaceTheCurrentValue() {
        Role current = role("admin", "Administrators", Map.of("level", List.of("high"), "team", List.of("ops")));

        Role merged = current.mergedWith(role("admin", "Admins", Map.of("level", List.of("low"))));

        assertThat(merged.description()).isEqualTo("Admins");
        assertThat(merged.attributes()).containsExactlyEntriesOf(Map.of("level", List.of("low")));
    }

    @Test
    void emptyAttributesClearThem() {
        Role current = role("admin", null, Map.of("level", List.of("high")));

        assertThat(current.mergedWith(role("admin", null, Map.of())).attributes())
                .isEmpty();
    }

    @Test
    void rolesWithDifferentKeysCannotBeMerged() {
        assertThatThrownBy(() -> role("a", null, null).mergedWith(role("b", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void realmAndClientRolesWithTheSameNameAreDifferent() {
        assertThat(RoleKey.realm("admin")).isNotEqualTo(RoleKey.client("app", "admin"));
        assertThat(RoleKey.client("app", "admin")).hasToString("app/admin");
    }

    @Test
    void defaultRolesAreOwnedByKeycloak() {
        assertThat(role("default-roles-test", "${role_default-roles}", null).isOwnedByKeycloak())
                .isTrue();
        assertThat(role("offline_access", "${role_offline-access}", null).isOwnedByKeycloak())
                .isTrue();
        assertThat(role("uma_authorization", "${role_uma_authorization}", null).isOwnedByKeycloak())
                .isTrue();
        assertThat(role("offline_access", "Custom description", null).isOwnedByKeycloak())
                .isFalse();
    }

    @Test
    void rolesOfKeycloakClientsAreOwnedByKeycloak() {
        Role managementRole = new Role(RoleKey.client("realm-management", "view-users"), null, null, null);
        Role applicationRole = new Role(RoleKey.client("my-app", "view-users"), null, null, null);

        assertThat(managementRole.isOwnedByKeycloak()).isTrue();
        assertThat(applicationRole.isOwnedByKeycloak()).isFalse();
    }
}
