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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImport;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImportParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;

class RealmDocumentsTest {

    private static ImportFile file(final String content) {
        return new ImportFile("location", "file:/realms/realm.yaml", content);
    }

    @Test
    void aJsonDocumentHoldsOneRealm() {
        List<RealmRepresentation> realms =
                RealmDocuments.parse(file("{\"realm\": \"a\", \"enabled\": true}"), RealmRepresentation.class);

        assertThat(realms).extracting(RealmRepresentation::getRealm).containsExactly("a");
    }

    @Test
    void aYamlFileMayHoldSeveralRealms() {
        List<RealmRepresentation> realms =
                RealmDocuments.parse(file("realm: a\n---\nrealm: b\n"), RealmRepresentation.class);

        assertThat(realms).extracting(RealmRepresentation::getRealm).containsExactly("a", "b");
    }

    @Test
    void anUnknownPropertyIsInvalid() {
        assertThatThrownBy(() -> RealmDocuments.parse(file("realm: a\nunknownKey: 1\n"), RealmRepresentation.class))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageStartingWith("Unable to parse file 'file:/realms/realm.yaml'")
                .hasMessageContaining("unknownKey");
    }

    @Test
    void anInvalidDocumentIsInvalid() {
        assertThatThrownBy(() -> RealmDocuments.parse(file("realm: [a"), RealmRepresentation.class))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageStartingWith("Unable to parse file 'file:/realms/realm.yaml'");
    }

    @Test
    void anImportFileRemembersItsSourceAndItsProtectionRules() {
        List<RealmImport> realms = new RealmImportParser()
                .parse(file("realm: a\nprotectedResources:\n  - matcher: legacy\n    type: CLIENT\n"));

        assertThat(realms).singleElement().satisfies(realm -> {
            assertThat(realm.getSource()).isEqualTo("file:/realms/realm.yaml");
            assertThat(realm.getProtectedResources()).hasSize(1);
        });
    }

    @Test
    void anInvalidProtectionRuleFailsWhenTheFileIsParsed() {
        assertThatThrownBy(() -> new RealmImportParser()
                        .parse(file("realm: a\nprotectedResources:\n  - rule: PATTERN\n    matcher: \"a(\"\n")))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("a(");
    }
}
