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
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
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

class ImportOpenedRealmTest {

    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());
    private static final ImportFile RENDERED = new ImportFile("demo", "00-realm.yaml", "realm: demo\n");

    private final FakeRealmDefinitions definitions = new FakeRealmDefinitions(DEMO);
    private final VariableValues remembered = new VariableValues();
    private final RecordingConnection connection = new RecordingConnection();

    private ImportOpenedRealm importOpenedRealm() {
        this.definitions.sources = Map.of("00-realm.yaml", "realm: demo\nurl: {{shopUrl}}\n");
        this.definitions.importFiles.add(RENDERED);
        return new ImportOpenedRealm(
                new ResolveVariables(
                        new ListVariables(this.definitions), name -> Optional.empty(), new ScriptedQuestions()),
                this.definitions);
    }

    @Test
    void theDryRunUsesTheRenderedFiles() {
        ImportOpenedRealm importOpenedRealm = importOpenedRealm();

        ImportReport report =
                importOpenedRealm.dryRun("demo", this.connection, Map.of("shopUrl", "https://shop"), this.remembered);

        assertThat(report).isSameAs(this.connection.report);
        assertThat(this.definitions.importedWith).containsExactly(Map.entry("shopUrl", "https://shop"));
        assertThat(this.connection.dryRuns).containsExactly(List.of(RENDERED));
        assertThat(this.connection.applied).isEmpty();
    }

    @Test
    void applyUsesTheSameValues() {
        ImportOpenedRealm importOpenedRealm = importOpenedRealm();

        PreparedImport prepared =
                importOpenedRealm.prepare("demo", this.connection, Map.of("shopUrl", "https://shop"), this.remembered);
        ImportReport report = importOpenedRealm.apply(prepared);

        assertThat(prepared.report()).isSameAs(this.connection.report);
        assertThat(report).isSameAs(this.connection.report);
        assertThat(this.definitions.importedWith).containsExactly(Map.entry("shopUrl", "https://shop"));
        assertThat(this.remembered.get("shopUrl")).contains("https://shop");
        assertThat(this.connection.applied).containsExactly(List.of(RENDERED));
    }

    @Test
    void applyImportsTheFilesOfThePlanEvenWhenTheFolderChangedSince() {
        ImportOpenedRealm importOpenedRealm = importOpenedRealm();
        PreparedImport prepared =
                importOpenedRealm.prepare("demo", this.connection, Map.of("shopUrl", "https://shop"), this.remembered);
        // the folder changes while the user reads the plan
        this.definitions.importFiles.clear();
        this.definitions.importFiles.add(new ImportFile("demo", "00-realm.yaml", "realm: demo\nenabled: false\n"));

        importOpenedRealm.apply(prepared);

        assertThat(this.connection.applied).singleElement().isSameAs(this.connection.dryRuns.getFirst());
        assertThat(this.connection.applied.getFirst()).containsExactly(RENDERED);
    }

    @Test
    void anInvalidFileAfterRenderingIsToldWithoutItsContent() {
        ImportOpenedRealm importOpenedRealm = importOpenedRealm();
        this.connection.failure = new InvalidImportException(
                "Unable to parse file '00-realm.yaml': found undefined alias s3cr3t-marker",
                new IllegalStateException("snippet: secret: *s3cr3t-marker"),
                "Unable to parse file '00-realm.yaml' at line 4, column 13");

        Throwable dryRun = catchThrowable(() -> importOpenedRealm.dryRun(
                "demo", this.connection, Map.of("shopUrl", "*s3cr3t-marker"), this.remembered));

        assertThat(dryRun)
                .isInstanceOf(InvalidImportException.class)
                .hasMessageStartingWith("Unable to parse file '00-realm.yaml' at line 4, column 13")
                .hasMessageNotContaining("s3cr3t-marker")
                .hasNoCause();
        assertThat(dryRun.getSuppressed()).isEmpty();
    }

    @Test
    void anInvalidFileWithoutLocationIsToldWithoutItsContent() {
        ImportOpenedRealm importOpenedRealm = importOpenedRealm();
        this.connection.failure = new InvalidImportException("bad value s3cr3t-marker");

        assertThatThrownBy(() -> importOpenedRealm.prepare(
                        "demo", this.connection, Map.of("shopUrl", "s3cr3t-marker"), this.remembered))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("'demo'")
                .hasMessageNotContaining("s3cr3t-marker")
                .hasNoCause();
    }

    /** Records the files it is given, and answers an empty report. */
    private static final class RecordingConnection implements Connection {

        final List<List<ImportFile>> dryRuns = new ArrayList<>();
        final List<List<ImportFile>> applied = new ArrayList<>();
        final ImportReport report = new ImportReport(Map.of("demo", List.of()));
        RuntimeException failure;

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
            if (this.failure != null) {
                throw this.failure;
            }
            this.dryRuns.add(files);
            return this.report;
        }

        @Override
        public ImportReport apply(final List<ImportFile> files) {
            this.applied.add(files);
            return this.report;
        }

        @Override
        public ExtractedRealm extract(final String realm, final boolean withUsers) {
            throw new UnsupportedOperationException("no extraction in this test");
        }

        @Override
        public ClientSecrets clientSecrets(final String realm) {
            throw new UnsupportedOperationException("no extraction in this test");
        }

        @Override
        public void close() {}
    }
}
