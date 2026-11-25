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

import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineBuiltInsTest {

    @Test
    void reads_the_reference_realm_of_the_version() {
        BuiltIns builtIns = new BaselineBuiltIns("26.5.3").builtIns();
        assertThat(builtIns.realmRoles()).contains("offline_access", "uma_authorization");
        assertThat(builtIns.clientIds()).contains("realm-management");
        assertThat(builtIns.clientRoles().get("realm-management")).contains("manage-users");
    }

    @Test
    void unknown_version_falls_back_to_the_most_recent() {
        BuiltIns builtIns = new BaselineBuiltIns("99.0.0").builtIns();
        assertThat(builtIns).isEqualTo(new BaselineBuiltIns("26.5.3").builtIns());
    }

    @Test
    void most_recent_is_compared_numerically() {
        assertThat(BaselineBuiltIns.mostRecent(List.of("9.0.0", "26.0.5", "26.5.3", "25.0.5")))
                .isEqualTo("26.5.3");
    }
}
