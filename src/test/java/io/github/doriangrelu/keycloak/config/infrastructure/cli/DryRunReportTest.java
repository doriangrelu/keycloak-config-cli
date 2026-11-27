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

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.reconcile.NotComparedValue;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DryRunReportTest {

    private static RealmSettings settings(String displayName, String smtpPassword) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("displayName", displayName);
        values.put("smtpServer", Map.of("password", smtpPassword));
        values.put("enabled", true);
        return new RealmSettings("realm", new UnmodeledAttributes(values));
    }

    private static Map<String, List<PlannedChange>> plans() {
        Map<String, List<PlannedChange>> plans = new LinkedHashMap<>();
        plans.put(
                "realm",
                List.of(
                        new PlannedChange(
                                ResourceType.REALM_SETTINGS,
                                "realm",
                                PlannedChange.Kind.UPDATE,
                                settings("Old", "old-secret"),
                                settings("New", "new-secret")),
                        new PlannedChange(
                                ResourceType.MESSAGE_BUNDLE,
                                "fr",
                                PlannedChange.Kind.CREATE,
                                null,
                                new MessageBundle("fr", Map.of("hello", "bonjour"))),
                        new PlannedChange(
                                ResourceType.MESSAGE_BUNDLE,
                                "de",
                                PlannedChange.Kind.DELETE,
                                new MessageBundle("de", Map.of()),
                                null)));
        plans.put("other", List.of());
        return plans;
    }

    @Test
    void listsTheChangedFieldsOfAnUpdate() {
        assertThat(DryRunReport.changedFields(settings("Old", "x"), settings("New", "x")))
                .containsExactly("displayName");
    }

    @Test
    void textReportShowsEachChangeWithoutAnyValue() {
        String text = DryRunReport.of(plans()).toText();

        assertThat(text)
                .contains("Realm 'realm': 3 change(s)")
                .contains("  ~ Realm settings 'realm' (displayName, smtpServer)")
                .contains("  + Message bundle 'fr'")
                .contains("  - Message bundle 'de'")
                .contains("Realm 'other': 0 change(s)")
                .contains("Dry run: 3 change(s) pending.")
                .doesNotContain("secret")
                .doesNotContain("New");
    }

    @Test
    void jsonReportCountsThePendingChanges() {
        DryRunReport report = DryRunReport.of(plans());

        assertThat(report.pendingChanges()).isEqualTo(3);
        assertThat(report.toJson())
                .contains("\"pendingChanges\" : 3")
                .contains("\"type\" : \"REALM_SETTINGS\"")
                .contains("\"change\" : \"update\"")
                .contains("\"fields\" : [ \"displayName\", \"smtpServer\" ]")
                .doesNotContain("secret");
    }

    private static ImportReport withHiddenValues() {
        Map<String, List<NotComparedValue>> notCompared = new LinkedHashMap<>();
        notCompared.put(
                "realm",
                List.of(
                        new NotComparedValue(ResourceType.REALM_SETTINGS, "realm", "smtpServer.password"),
                        new NotComparedValue(ResourceType.IDENTITY_PROVIDER, "google", "config.clientSecret")));
        return new ImportReport(Map.of("realm", List.of()), notCompared);
    }

    @Test
    void theHiddenValuesAreNotedOnTheirOwnLineAndAreNoChange() {
        DryRunReport report = DryRunReport.of(withHiddenValues());

        assertThat(report.pendingChanges()).isZero();
        assertThat(report.toText())
                .contains(String.format("Realm 'realm': 0 change(s)%n"
                        + "  (not compared: smtpServer.password of Realm settings 'realm', Keycloak hides it)%n"
                        + "  (not compared: config.clientSecret of Identity provider 'google', Keycloak hides it)%n"))
                .contains("Dry run: no change pending.");
    }

    @Test
    void theJsonReportListsTheHiddenValues() {
        assertThat(DryRunReport.of(withHiddenValues()).toJson())
                .contains("\"pendingChanges\" : 0")
                .contains("\"notCompared\" : [ {")
                .contains("\"type\" : \"IDENTITY_PROVIDER\"")
                .contains("\"key\" : \"google\"")
                .contains("\"field\" : \"config.clientSecret\"");
    }

    @Test
    void aReportWithoutHiddenValueHasNoNote() {
        DryRunReport report = DryRunReport.of(new ImportReport(plans()));

        assertThat(report.toText()).doesNotContain("not compared");
        assertThat(report.toJson()).doesNotContain("notCompared");
    }

    @Test
    void anEmptyPlanHasNoPendingChange() {
        DryRunReport report = DryRunReport.of(Map.of("realm", List.of()));

        assertThat(report.pendingChanges()).isZero();
        assertThat(report.toText()).contains("Dry run: no change pending.");
    }
}
