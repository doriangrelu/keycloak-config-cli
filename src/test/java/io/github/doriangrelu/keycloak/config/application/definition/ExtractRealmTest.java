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

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtractRealmTest {

    private static final String SECRET = "s3cr3t-marker-of-shop";
    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());
    private static final String DOCUMENT = "{\"realm\":\"shop\",\"clients\":[{\"clientId\":\"shop\","
            + "\"secret\":\"{{shopSecret}}\"}],\"smtpServer\":{\"password\":\"{{smtpPassword}}\"}}";

    private final FakeRealmDefinitions definitions = new FakeRealmDefinitions(DEMO);
    private final ExtractRealm extractRealm =
            new ExtractRealm(this.definitions, new CreateRealm(this.definitions, Rules.none()));
    private final ExtractingConnection connection = new ExtractingConnection();

    @Test
    void theFolderIsCheckedBeforeKeycloakIsCalled() {
        assertThatThrownBy(() -> this.extractRealm.extract(
                        "shop", Optional.of("Demo"), DefinitionFormat.YAML, false, this.connection))
                .isInstanceOf(RealmFolderExistsException.class);
        assertThatThrownBy(() -> this.extractRealm.extract(
                        "shop", Optional.of("../x"), DefinitionFormat.YAML, false, this.connection))
                .isInstanceOf(InvalidRealmFolderException.class);

        assertThat(this.connection.calls).isEmpty();
        assertThat(this.definitions.extracted).isEmpty();
    }

    @Test
    void theFolderIsTheRealmByDefault() {
        ExtractRealm.Extraction extraction =
                this.extractRealm.extract("shop", Optional.empty(), DefinitionFormat.JSON, true, this.connection);

        assertThat(extraction.folder()).isEqualTo("shop");
        assertThat(extraction.files()).containsExactly("00-realm.json");
        assertThat(this.connection.calls).containsExactly("extract shop true", "secrets shop");
        assertThat(this.definitions.extracted).containsExactly("shop:JSON:" + DOCUMENT);
    }

    @Test
    void aFailureWritesNothing() {
        this.connection.failure = new RealmNotFoundOnTargetException("shop", this.connection.target());

        assertThatThrownBy(() -> this.extractRealm.extract(
                        "shop", Optional.empty(), DefinitionFormat.YAML, false, this.connection))
                .isInstanceOf(RealmNotFoundOnTargetException.class);

        assertThat(this.definitions.extracted).isEmpty();
    }

    @Test
    void clientSecretsAreNotInTheVariablesToGive() {
        ExtractRealm.Extraction extraction =
                this.extractRealm.extract("shop", Optional.empty(), DefinitionFormat.YAML, false, this.connection);

        assertThat(extraction.variablesToGive()).containsExactly("smtpPassword");
        assertThat(extraction.clientSecretsKept()).isTrue();
        assertThat(extraction.clientSecrets()).containsExactly(Map.entry("shopSecret", SECRET));
        assertThat(extraction.warnings()).containsExactly("Default realm role 'offline_access' was deleted");
        assertThat(extraction.skippedUsers()).isEqualTo(2);
        assertThat(extraction.toString()).doesNotContain(SECRET);
    }

    @Test
    void unreadableClientSecretsAreToBeGivenAndTheExtractionStays() {
        this.connection.secretsFailure = new KeycloakUnreachableException(
                this.connection.target(), "connection refused", new IllegalStateException(SECRET));

        ExtractRealm.Extraction extraction =
                this.extractRealm.extract("shop", Optional.empty(), DefinitionFormat.YAML, false, this.connection);

        assertThat(this.definitions.extracted).hasSize(1);
        assertThat(extraction.variablesToGive()).containsExactly("shopSecret", "smtpPassword");
        assertThat(extraction.clientSecretsKept()).isFalse();
        assertThat(extraction.clientSecrets()).isEmpty();
        assertThat(extraction.warnings())
                .contains("The client secrets could not be read from local: give them at import")
                .allSatisfy(warning -> assertThat(warning).doesNotContain(SECRET));
    }

    /** Answers an extracted realm and the secret of its client, and records the calls. */
    private static final class ExtractingConnection implements Connection {

        final List<String> calls = new ArrayList<>();
        RuntimeException failure;
        RuntimeException secretsFailure;

        @Override
        public Target target() {
            return new Target(
                    "local",
                    "http://localhost:8080",
                    "master",
                    "admin-cli",
                    GrantType.PASSWORD,
                    Optional.of("admin"),
                    true,
                    Duration.ofSeconds(5));
        }

        @Override
        public String keycloakVersion() {
            return "26.5.3";
        }

        @Override
        public ImportReport dryRun(final List<ImportFile> files) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ImportReport apply(final List<ImportFile> files) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExtractedRealm extract(final String realm, final boolean withUsers) {
            this.calls.add("extract " + realm + " " + withUsers);
            if (this.failure != null) {
                throw this.failure;
            }
            return new ExtractedRealm(
                    realm,
                    DOCUMENT,
                    List.of("smtpPassword", "shopSecret"),
                    List.of("Default realm role 'offline_access' was deleted"),
                    2);
        }

        @Override
        public ClientSecrets clientSecrets(final String realm) {
            this.calls.add("secrets " + realm);
            if (this.secretsFailure != null) {
                throw this.secretsFailure;
            }
            return ClientSecrets.of(Map.of("shopSecret", SECRET));
        }

        @Override
        public void close() {}
    }
}
