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

import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FormatRealmTest {

    private FakeRealmDefinitions definitions;

    private FormatRealm formatRealm(final RealmDefinition realm) {
        this.definitions = new FakeRealmDefinitions(realm);
        this.definitions.unformatted.add("20-clients.yaml");
        return new FormatRealm(this.definitions);
    }

    private static RealmDefinition realm(final List<UnreadableFile> unreadable) {
        return new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), unreadable);
    }

    @Test
    void theFilesOfARealmAreFormatted() {
        assertThat(formatRealm(realm(List.of())).format("demo")).containsExactly("20-clients.yaml");
        assertThat(this.definitions.formatted).containsExactly("demo");
    }

    @Test
    void aReadOnlyRealmIsNotFormatted() {
        FormatRealm formatRealm = formatRealm(realm(List.of(new UnreadableFile("00-realm.yaml", 2, "unquoted"))));

        assertThatThrownBy(() -> formatRealm.format("demo"))
                .isInstanceOf(ReadOnlyRealmException.class)
                .hasMessageContaining("00-realm.yaml:2");
        assertThat(this.definitions.formatted).isEmpty();
    }

    @Test
    void anUnknownRealmIsReported() {
        assertThatThrownBy(() -> formatRealm(realm(List.of())).format("nope"))
                .isInstanceOf(UnknownRealmFolderException.class);
    }
}
