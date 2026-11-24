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

import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateRealmTest {

    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());
    private static final TemplatedValue REALM = TemplatedValue.of("{{env}}-mon-realm");

    private final FakeRealmDefinitions definitions = new FakeRealmDefinitions(DEMO);
    private final CreateRealm createRealm = new CreateRealm(this.definitions);

    @Test
    void aNewFolderIsCreatedThroughThePort() {
        String file = this.createRealm.create("mon-realm", REALM, DefinitionFormat.YAML);

        assertThat(file).isEqualTo("00-realm.yaml");
        assertThat(this.definitions.created).containsExactly("mon-realm");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", ".x", "a/b", "a\\b", "", "..", "-x", "demo."})
    void anInvalidFolderNameIsRefusedBeforeAnyWrite(final String folder) {
        assertThatThrownBy(() -> this.createRealm.create(folder, REALM, DefinitionFormat.YAML))
                .isInstanceOf(InvalidRealmFolderException.class);

        assertThat(this.definitions.created).isEmpty();
    }

    @Test
    void anExistingFolderIsRefusedBeforeAnyWrite() {
        assertThatThrownBy(() -> this.createRealm.create("demo", REALM, DefinitionFormat.JSON))
                .isInstanceOf(RealmFolderExistsException.class)
                .hasMessage("Realm folder 'demo' already exists: use 'realm open demo'");

        assertThat(this.definitions.created).isEmpty();
    }

    @Test
    void aFolderThatDiffersByCaseOnlyIsRefusedBeforeAnyWrite() {
        assertThatThrownBy(() -> this.createRealm.create("Demo", REALM, DefinitionFormat.JSON))
                .isInstanceOf(RealmFolderExistsException.class);

        assertThat(this.definitions.created).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"my realm", "{{x", "demo.", "../x"})
    void checkFolderRefusesAnInvalidName(final String folder) {
        assertThatThrownBy(() -> this.createRealm.checkFolder(folder)).isInstanceOf(InvalidRealmFolderException.class);
    }

    @Test
    void checkFolderRefusesAnExistingFolderIgnoringTheCase() {
        assertThatThrownBy(() -> this.createRealm.checkFolder("DEMO")).isInstanceOf(RealmFolderExistsException.class);
    }

    @Test
    void checkFolderAcceptsAFreeFolderAndWritesNothing() {
        this.createRealm.checkFolder("mon-realm");

        assertThat(this.definitions.created).isEmpty();
    }
}
