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

package io.github.doriangrelu.keycloak.config.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.importation.ImportOptions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.application.importation.port.RealmDeclarationParser;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.NotComparedValue;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.DryRunReport;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The secrets Keycloak hides (the SMTP password, the secret of an identity provider): a second import of the same
 * files changes nothing, and the dry run notes them as not compared.
 */
class ImportHiddenValuesIT extends AbstractImportIT {

    private static final String REALM_NAME = "realmWithHiddenValues";

    @Autowired
    private ImportFileSource files;

    @Autowired
    private RealmDeclarationParser parser;

    @Autowired
    private RealmGateway realms;

    @Autowired
    private ComponentGateway components;

    @Autowired
    private ReconciliationEngine engine;

    ImportHiddenValuesIT() {
        this.resourcePath = "import-files/hidden-values";
    }

    private ImportReport dryRun(final String fileName) {
        ImportRealms dryRun = new ImportRealms(
                this.files, this.parser, this.realms, this.components, this.engine, new ImportOptions(true, false));
        return dryRun.importRealms(getImport(fileName).stream()
                .map(realmImportDeclarations::declarationOf)
                .toList());
    }

    @Test
    @Order(0)
    void aSecondImportOfTheSameFilesChangesNothing() throws Exception {
        doImport("00_realm.json");

        doImport("00_realm.json");

        assertThat(lastImport.plan(REALM_NAME)).isEmpty();
    }

    @Test
    @Order(1)
    void theDryRunHasNoChangeAndNotesTheHiddenValues() {
        ImportReport report = dryRun("00_realm.json");

        assertThat(report.plan(REALM_NAME)).isEmpty();
        assertThat(report.notCompared(REALM_NAME))
                .containsExactlyInAnyOrder(
                        new NotComparedValue(ResourceType.REALM_SETTINGS, REALM_NAME, "smtpServer.password"),
                        new NotComparedValue(ResourceType.IDENTITY_PROVIDER, "google", "config.clientSecret"));
        DryRunReport text = DryRunReport.of(report);
        assertThat(text.pendingChanges()).isZero();
        assertThat(text.toText())
                .contains("  (not compared: smtpServer.password of Realm settings '" + REALM_NAME
                        + "', Keycloak hides it)")
                .contains("  (not compared: config.clientSecret of Identity provider 'google', Keycloak hides it)")
                .contains("Dry run: no change pending.")
                .doesNotContain("smtp-password-value")
                .doesNotContain("google-secret-value");
    }

    @Test
    @Order(2)
    void anotherChangeIsPlannedWithoutTheHiddenField() {
        ImportReport report = dryRun("01_realm_renamed.json");

        assertThat(report.plan(REALM_NAME)).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ResourceType.REALM_SETTINGS);
            assertThat(change.kind()).isEqualTo(PlannedChange.Kind.UPDATE);
        });
        assertThat(DryRunReport.of(report).toText())
                .contains("~ Realm settings '" + REALM_NAME + "' (displayName)")
                .doesNotContain("smtp-password-value");
    }
}
