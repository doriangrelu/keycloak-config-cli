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

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadRealmsTest {

    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());

    private final ReadRealms readRealms = new ReadRealms(new RealmDefinitions() {
        @Override
        public List<String> folders() {
            return List.of("demo");
        }

        @Override
        public Optional<RealmDefinition> load(final String folder) {
            return folder.equals("demo") ? Optional.of(DEMO) : Optional.empty();
        }

        @Override
        public List<String> unformattedFiles(final String folder) {
            return List.of();
        }

        @Override
        public List<String> format(final String folder) {
            return List.of();
        }
    });

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
}
