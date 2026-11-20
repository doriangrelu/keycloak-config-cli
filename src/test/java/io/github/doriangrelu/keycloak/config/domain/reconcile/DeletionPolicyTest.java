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

package io.github.doriangrelu.keycloak.config.domain.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.reconcile.ProtectionRule.Matching;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeletionPolicyTest {

    @Test
    void deletesWhatNoRuleProtects() {
        DeletionPolicy policy = new DeletionPolicy(List.of());

        assertThat(policy.allowsDeletionOf(ResourceType.CLIENT, "anything")).isTrue();
    }

    @Test
    void resourcesProtectedByEqualityOrPatternAreNeverDeleted() {
        DeletionPolicy policy = new DeletionPolicy(List.of(
                new ProtectionRule(Matching.EQUALS, "legacy-app"), new ProtectionRule(Matching.PATTERN, "team-.*")));

        assertThat(policy.allowsDeletionOf(ResourceType.CLIENT, "legacy-app")).isFalse();
        assertThat(policy.allowsDeletionOf(ResourceType.CLIENT, "team-reader")).isFalse();
        assertThat(policy.allowsDeletionOf(ResourceType.CLIENT, "legacy-app-2")).isTrue();
        assertThat(policy.allowsDeletionOf(ResourceType.CLIENT, "other")).isTrue();
    }
}
