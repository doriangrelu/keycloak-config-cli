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
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.DryRunReport;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.util.CloneUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"import.dry-run=true"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportDryRunIT extends AbstractImportIT {

    private static final String REALM_NAME = "realmForDryRun";

    ImportDryRunIT() {
        this.resourcePath = "import-files/dry-run";
    }

    private List<PlannedChange> plan() {
        return realmImportService.plans().getOrDefault(REALM_NAME, List.of());
    }

    private static boolean has(List<PlannedChange> plan, ResourceType type, String key, PlannedChange.Kind kind) {
        return plan.stream()
                .anyMatch(change ->
                        change.type() == type && String.valueOf(change.key()).equals(key) && change.kind() == kind);
    }

    private boolean realmExists() {
        return keycloakProvider.getInstance().realms().findAll().stream()
                .anyMatch(realm -> REALM_NAME.equals(realm.getRealm()));
    }

    @Test
    @Order(0)
    void plansTheCreationOfAMissingRealmWithoutCreatingIt() throws Exception {
        doImport("00_realm.json");

        List<PlannedChange> plan = plan();
        assertThat(realmExists()).isFalse();
        assertThat(has(plan, ResourceType.REALM_SETTINGS, REALM_NAME, PlannedChange.Kind.CREATE))
                .isTrue();
        assertThat(has(plan, ResourceType.CLIENT, "app", PlannedChange.Kind.CREATE))
                .isTrue();
        assertThat(has(plan, ResourceType.REALM_ROLE, "reader", PlannedChange.Kind.CREATE))
                .isTrue();
        assertThat(has(plan, ResourceType.GROUP, "/team", PlannedChange.Kind.CREATE))
                .isTrue();
    }

    @Test
    @Order(1)
    void plansNothingForAnUnchangedRealm() throws Exception {
        RealmImport realm = getFirstImport("00_realm.json");
        keycloakProvider.getInstance().realms().create(CloneUtil.deepClone(realm, RealmRepresentation.class));

        doImport("00_realm.json");

        List<PlannedChange> plan = plan();
        assertThat(plan).isEmpty();
    }

    @Test
    @Order(2)
    void plansTheChangesOfAnExistingRealmWithoutApplyingThem() throws Exception {
        doImport("01_changed_realm.json");

        List<PlannedChange> plan = plan();
        DryRunReport report = DryRunReport.of(Map.of(REALM_NAME, plan));
        assertThat(has(plan, ResourceType.REALM_ROLE, "writer", PlannedChange.Kind.CREATE))
                .isTrue();
        assertThat(has(plan, ResourceType.GROUP, "/team", PlannedChange.Kind.DELETE))
                .isTrue();
        assertThat(report.toText()).contains("~ Realm settings 'realmForDryRun' (displayName)");
        assertThat(report.toText()).containsPattern("~ Client 'app' \\(.*redirectUris.*\\)");

        RealmRepresentation realm =
                keycloakProvider.getInstance().realm(REALM_NAME).toRepresentation();
        assertThat(realm.getDisplayName()).isEqualTo("Dry run");
        assertThat(keycloakProvider.getInstance().realm(REALM_NAME).roles().list())
                .noneMatch(role -> "writer".equals(role.getName()));
        assertThat(keycloakProvider.getInstance().realm(REALM_NAME).groups().groups())
                .anyMatch(group -> "team".equals(group.getName()));
        ClientRepresentation app = keycloakProvider
                .getInstance()
                .realm(REALM_NAME)
                .clients()
                .findByClientId("app")
                .getFirst();
        assertThat(app.getRedirectUris()).containsExactly("https://app.example.org/*");
    }
}
