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

package io.github.doriangrelu.keycloak.config.domain.group.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroupTest {

    private static Group group(Set<String> realmRoles, Map<String, Set<String>> clientRoles) {
        return new Group("/parent/child", Map.of("a", List.of("1")), realmRoles, clientRoles, Set.of(), null);
    }

    @Test
    void pathGivesTheNameAndTheParent() {
        Group child = new Group("/parent/child", null, null, null, null, null);

        assertThat(child.name()).isEqualTo("child");
        assertThat(child.parentPath()).isEqualTo("/parent");
        assertThat(new Group("/parent", null, null, null, null, null).parentPath())
                .isNull();
        assertThat(Group.childPath("/parent", "child")).isEqualTo("/parent/child");
        assertThat(Group.childPath(null, "parent")).isEqualTo("/parent");
    }

    @Test
    void declaredRoleMappingsReplaceTheCurrentOnes() {
        Group current = group(Set.of("a", "b"), Map.of("app", Set.of("x")));

        Group merged = current.mergedWith(
                new Group("/parent/child", null, Set.of("c"), Map.of("other", Set.of("y")), null, null), false);

        assertThat(merged.realmRoles()).containsExactly("c");
        assertThat(merged.clientRoles()).containsExactlyEntriesOf(Map.of("other", Set.of("y")));
    }

    @Test
    void undeclaredRoleMappingsAreKeptOnlyOutsideFullMode() {
        Group current = group(Set.of("a"), Map.of("app", Set.of("x")));
        Group withoutRoles = new Group("/parent/child", null, null, null, null, null);

        assertThat(current.mergedWith(withoutRoles, false)).isEqualTo(current);
        Group cleaned = current.mergedWith(withoutRoles, true);
        assertThat(cleaned.realmRoles()).isEmpty();
        assertThat(cleaned.clientRoles()).isEmpty();
    }

    @Test
    void declaredAttributesReplaceTheCurrentOnes() {
        Group current = group(Set.of(), Map.of());

        Group merged = current.mergedWith(
                new Group("/parent/child", Map.of("b", List.of("2")), null, null, null, null), false);

        assertThat(merged.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of("b", List.of("2")));
    }
}
