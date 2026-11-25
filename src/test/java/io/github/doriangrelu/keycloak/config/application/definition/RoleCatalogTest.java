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

import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleCatalogTest {

    private static Defined<RoleDefinition> role(final String name, final String clientId) {
        return new Defined<>(
                new RoleDefinition(
                        TemplatedValue.of(name),
                        Optional.ofNullable(clientId).map(TemplatedValue::of),
                        Optional.empty()),
                new Origin("30-roles.yaml", 3));
    }

    @SafeVarargs
    private static RealmDefinition realm(final String name, final Defined<RoleDefinition>... roles) {
        return new RealmDefinition(
                "demo",
                Optional.ofNullable(name).map(TemplatedValue::of),
                List.of(),
                List.of(roles),
                List.of(),
                Set.of(ElementType.ROLE),
                List.of());
    }

    private static List<String> labels(final RealmDefinition realm, final BuiltIns builtIns) {
        return RoleCatalog.of(realm, builtIns).stream()
                .map(AssignableRole::label)
                .toList();
    }

    @Test
    void folderRolesComeFirst() {
        RealmDefinition realm =
                realm(null, role("writer", "shop"), role("reader", null), role("admin", "back"), role("admin", null));
        BuiltIns builtIns = new BuiltIns(Set.of(), Set.of("offline_access"), Map.of("account", Set.of("view")));

        assertThat(labels(realm, builtIns))
                .containsExactly("admin", "reader", "back/admin", "shop/writer", "offline_access", "account/view");
    }

    @Test
    void keycloakRolesAreMarked() {
        RealmDefinition realm = realm(null, role("admin", null), role("writer", "shop"));
        BuiltIns builtIns = new BuiltIns(Set.of(), Set.of("offline_access"), Map.of("account", Set.of("view")));

        List<AssignableRole> catalogue = RoleCatalog.of(realm, builtIns);

        assertThat(catalogue).extracting(AssignableRole::builtIn).containsExactly(false, false, true, true);
        assertThat(catalogue)
                .extracting(AssignableRole::explanation)
                .containsExactly("realm", "client shop", "realm (Keycloak)", "client account (Keycloak)");
    }

    @Test
    void theDefaultRolesNeedALiteralRealmName() {
        BuiltIns builtIns = new BuiltIns(Set.of(), Set.of("default-roles-realm_name_placeholder"), Map.of());

        assertThat(labels(realm(null), builtIns)).isEmpty();
        assertThat(labels(realm("{{env}}-demo"), builtIns)).isEmpty();
        assertThat(labels(realm("Demo"), builtIns)).containsExactly("default-roles-demo");
    }

    @Test
    void aRoleOfTheFolderIsNotRepeatedAsKeycloakRole() {
        RealmDefinition realm = realm("demo", role("offline_access", null), role("view", "account"));
        BuiltIns builtIns = new BuiltIns(Set.of(), Set.of("offline_access"), Map.of("account", Set.of("view")));

        List<AssignableRole> catalogue = RoleCatalog.of(realm, builtIns);

        assertThat(catalogue)
                .extracting(AssignableRole::label)
                .containsExactly("offline_access", "account/view", "default-roles-demo");
        assertThat(catalogue.stream().filter(AssignableRole::builtIn).map(AssignableRole::label))
                .containsExactly("default-roles-demo");
    }
}
