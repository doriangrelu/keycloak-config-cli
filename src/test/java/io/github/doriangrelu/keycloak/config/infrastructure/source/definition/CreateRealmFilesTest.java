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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateRealmFilesTest {

    private static final TemplatedValue REALM = TemplatedValue.of("{{env}}-mon-realm");

    @TempDir
    Path root;

    @Test
    void aYamlRealmFileHoldsTheRealmAndItsEnabledFlag() throws IOException {
        RealmDefinitionFiles files = new RealmDefinitionFiles(this.root);

        String name = files.createRealm("mon-realm", REALM, DefinitionFormat.YAML);

        assertThat(name).isEqualTo("00-realm.yaml");
        String content = Files.readString(this.root.resolve("mon-realm/00-realm.yaml"), StandardCharsets.UTF_8);
        assertThat(content).contains("realm: \"{{env}}-mon-realm\"").contains("enabled: true");
        assertThat(files.load("mon-realm").orElseThrow().realm()).contains(REALM);
        assertThat(files.unformattedFiles("mon-realm")).isEmpty();
    }

    @Test
    void aJsonRealmFileHoldsTheSameContent() throws IOException {
        RealmDefinitionFiles files = new RealmDefinitionFiles(this.root);

        String name = files.createRealm("mon-realm", REALM, DefinitionFormat.JSON);

        assertThat(name).isEqualTo("00-realm.json");
        String content = Files.readString(this.root.resolve("mon-realm/00-realm.json"), StandardCharsets.UTF_8);
        assertThat(content).contains("\"realm\"").contains("{{env}}-mon-realm").contains("\"enabled\"");
        assertThat(files.load("mon-realm").orElseThrow().realm()).contains(REALM);
        assertThat(files.unformattedFiles("mon-realm")).isEmpty();
    }
}
