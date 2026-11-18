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

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleCompositesTest {

    private static final RoleKey OWNER = RoleKey.realm("composite");

    @Test
    void unspecifiedPartsAreKept() {
        RoleComposites current = new RoleComposites(OWNER, Set.of("a"), Map.of("app", Set.of("b")));

        assertThat(current.mergedWith(new RoleComposites(OWNER, null, null))).isEqualTo(current);
    }

    @Test
    void aDeclaredPartReplacesTheCurrentOne() {
        RoleComposites current =
                new RoleComposites(OWNER, Set.of("a"), Map.of("app", Set.of("b"), "other", Set.of("c")));

        RoleComposites merged = current.mergedWith(new RoleComposites(OWNER, null, Map.of("app", Set.of("d"))));

        assertThat(merged.realm()).containsExactly("a");
        assertThat(merged.client()).containsExactlyEntriesOf(Map.of("app", Set.of("d")));
    }

    @Test
    void clientsWithoutRoleAreDropped() {
        RoleComposites declared = new RoleComposites(OWNER, Set.of(), Map.of("app", Set.of()));

        assertThat(declared).isEqualTo(RoleComposites.none(OWNER));
    }

    @Test
    void listsEveryIncludedRole() {
        RoleComposites composites = new RoleComposites(OWNER, Set.of("a"), Map.of("app", Set.of("b")));

        assertThat(composites.roles()).containsExactlyInAnyOrder(RoleKey.realm("a"), RoleKey.client("app", "b"));
        assertThat(new RoleComposites(OWNER, null, null).roles()).isEmpty();
    }
}
