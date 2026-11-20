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

package io.github.doriangrelu.keycloak.config.domain.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Rules of the normalization: comparison with the reference realm, and what a resource keeps of its values. */
class ReferenceComparisonTest {

    record Item(String name, String value) implements Resource<String> {
        @Override
        public String key() {
            return this.name;
        }

        @Override
        public String stateName() {
            return this.name;
        }
    }

    @Test
    void keepsTheNewAndChangedResourcesAndReportsTheMissingOnes() {
        ReferenceComparison<String, Item> comparison = ReferenceComparison.of(
                List.of(new Item("same", "1"), new Item("changed", "new"), new Item("added", "x")),
                List.of(new Item("same", "1"), new Item("changed", "old"), new Item("deleted", "y")));

        assertThat(comparison.differing()).containsExactly(new Item("changed", "new"), new Item("added", "x"));
        assertThat(comparison.missing()).containsExactly("deleted");
    }

    @Test
    void comparesWithTheGivenRule() {
        ReferenceComparison<String, Item> comparison = ReferenceComparison.of(
                List.of(new Item("a", "VALUE")),
                List.of(new Item("a", "value")),
                (exported, reference) -> exported.value().equalsIgnoreCase(reference.value()));

        assertThat(comparison.differing()).isEmpty();
    }

    @Test
    void attributesKeepTheirDifferingValuesOnly() {
        UnmodeledAttributes exported = new UnmodeledAttributes(map(
                "same", "1",
                "changed", "new",
                "added", "x",
                "kept", "k",
                "empty", null,
                "byEntry", Map.of("same", "1", "changed", "new")));
        UnmodeledAttributes reference = new UnmodeledAttributes(
                map("same", "1", "changed", "old", "kept", "k", "byEntry", Map.of("same", "1", "changed", "old")));

        UnmodeledAttributes differing = exported.differingFrom(reference, Set.of("byEntry"), Set.of("kept"));

        assertThat(differing.values())
                .containsExactly(
                        Map.entry("changed", "new"),
                        Map.entry("added", "x"),
                        Map.entry("kept", "k"),
                        Map.entry("byEntry", Map.of("changed", "new")));
    }

    @Test
    void realmSettingsKeepTheirDifferingValuesAndWhetherTheRealmIsEnabled() {
        RealmSettings exported = new RealmSettings(
                "realm",
                new UnmodeledAttributes(map(
                        "enabled",
                        true,
                        "displayName",
                        "Mine",
                        "sslRequired",
                        "external",
                        "attributes",
                        Map.of("frontendUrl", "https://login", "cibaInterval", "5"))));
        RealmSettings reference = new RealmSettings(
                "realm",
                new UnmodeledAttributes(
                        map("enabled", true, "sslRequired", "external", "attributes", Map.of("cibaInterval", "5"))));

        assertThat(exported.withoutValuesOf(reference).settings().values())
                .containsExactly(
                        Map.entry("enabled", true),
                        Map.entry("displayName", "Mine"),
                        Map.entry("attributes", Map.of("frontendUrl", "https://login")));
    }

    @Test
    void flowBindingsKeepTheirDifferingBindings() {
        RealmFlowBindings exported = new RealmFlowBindings(Map.of("browserFlow", "mine", "directGrantFlow", "direct"));
        RealmFlowBindings reference =
                new RealmFlowBindings(Map.of("browserFlow", "browser", "directGrantFlow", "direct"));

        assertThat(exported.withoutValuesOf(reference).flows()).containsExactly(Map.entry("browserFlow", "mine"));
        assertThat(reference.withoutValuesOf(reference).isEmpty()).isTrue();
    }

    @Test
    void clientsKeepWhatTellsWhatTheyAreAndTheirDifferingValues() {
        Client exported = new Client(
                "app",
                "App",
                Map.of("same", "1", "changed", "new"),
                Set.of("profile"),
                null,
                null,
                "secret",
                false,
                true,
                false,
                new UnmodeledAttributes(map("protocol", "openid-connect", "rootUrl", "https://app")));
        Client reference = new Client(
                "app",
                "",
                Map.of("same", "1", "changed", "old"),
                Set.of("profile"),
                null,
                null,
                null,
                false,
                false,
                false,
                new UnmodeledAttributes(map("protocol", "openid-connect", "rootUrl", "")));

        Client normalized = exported.withoutValuesOf(reference);

        assertThat(normalized)
                .isEqualTo(new Client(
                        "app",
                        "App",
                        Map.of("changed", "new"),
                        null,
                        null,
                        null,
                        "secret",
                        null,
                        true,
                        null,
                        new UnmodeledAttributes(map("protocol", "openid-connect", "rootUrl", "https://app"))));
        assertThat(exported.hasValuesDifferentFrom(reference)).isTrue();
    }

    @Test
    void aClientMissingAValueOfTheReferenceHasNoDifferenceAnImportCouldApply() {
        Client reference = new Client(
                "admin-cli", null, Map.of("a", "1", "b", "2"), null, null, null, null, null, true, null, null);
        Client exported =
                new Client("admin-cli", null, Map.of("a", "1"), null, null, null, null, null, true, null, null);

        assertThat(exported.hasValuesDifferentFrom(reference)).isFalse();
    }

    @Test
    void componentsCompareTheirConfigurationWhateverTheOrderOfItsValues() {
        Component.Key key = new Component.Key(null, "policy", "anonymous", "Allowed types");
        Component exported = new Component(key, null, "allowed", Map.of("types", List.of("a", "b")));
        Component reference = new Component(key, "id", "allowed", Map.of("types", List.of("b", "a")));

        assertThat(exported.hasSameContentAs(reference)).isTrue();
        assertThat(exported.hasSameContentAs(new Component(key, null, "allowed", Map.of("types", List.of("a")))))
                .isFalse();
    }

    private static Map<String, Object> map(final Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
