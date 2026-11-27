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

import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange.Kind;
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DeclarationDeletionsTest {

    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());
    private static final Target DEV = new Target(
            "dev",
            "https://dev.example.org",
            "master",
            "cli",
            GrantType.CLIENT_CREDENTIALS,
            Optional.empty(),
            true,
            Duration.ofSeconds(5));

    private final FakeRealmDefinitions definitions = new FakeRealmDefinitions(DEMO);
    private final Map<String, String> environment = new HashMap<>();
    private final VariableValues remembered = new VariableValues();
    private final List<List<ImportFile>> simulated = new ArrayList<>();
    private Supplier<ImportReport> report = () -> new ImportReport(Map.of());

    private final Connection connection = new Connection() {
        @Override
        public Target target() {
            return DEV;
        }

        @Override
        public String keycloakVersion() {
            return "26.5.3";
        }

        @Override
        public ImportReport dryRun(final List<ImportFile> files) {
            simulated.add(files);
            return report.get();
        }

        @Override
        public ImportReport apply(final List<ImportFile> files) {
            throw new AssertionError("a simulation never applies");
        }

        @Override
        public ExtractedRealm extract(final String realm, final boolean withUsers) {
            throw new UnsupportedOperationException("no extraction in this test");
        }

        @Override
        public Map<String, String> clientSecrets(final String realm) {
            throw new UnsupportedOperationException("no extraction in this test");
        }

        @Override
        public void close() {}
    };

    /** A Questions that fails when called: the simulation asks nothing. */
    private static Questions noQuestion() {
        return (Questions) Proxy.newProxyInstance(
                DeclarationDeletionsTest.class.getClassLoader(),
                new Class<?>[] {Questions.class},
                (proxy, method, args) -> {
                    throw new AssertionError("no question expected: " + method.getName());
                });
    }

    private DeclarationDeletions deletions() {
        ResolveVariables resolver = new ResolveVariables(
                new ListVariables(this.definitions),
                name -> Optional.ofNullable(this.environment.get(name)),
                noQuestion());
        return new DeclarationDeletions(resolver, this.definitions);
    }

    private static ClientDefinition client(final String clientId) {
        return new ClientDefinition(
                TemplatedValue.of(clientId),
                Optional.empty(),
                Optional.empty(),
                ApplicationType.SPA,
                List.of(),
                List.of(),
                Map.of(),
                Optional.empty());
    }

    private static RoleDefinition realmRole(final String name) {
        return new RoleDefinition(TemplatedValue.of(name), Optional.empty(), Optional.empty());
    }

    private static RoleDefinition clientRole(final String clientId, final String name) {
        return new RoleDefinition(TemplatedValue.of(name), Optional.of(TemplatedValue.of(clientId)), Optional.empty());
    }

    private static GroupDefinition group(final String path) {
        return new GroupDefinition(path, TemplatedValue.of(path), Set.of(), Map.of());
    }

    private static PlannedChange delete(final ResourceType type, final Object key) {
        return new PlannedChange(type, key, Kind.DELETE, null, null);
    }

    private void plans(final PlannedChange... changes) {
        ImportReport result = new ImportReport(Map.of("demo", List.of(changes)));
        this.report = () -> result;
    }

    private void fails(final RuntimeException failure) {
        this.report = () -> {
            throw failure;
        };
    }

    private String warning(final ElementDefinition element) {
        return deletions().warning("demo", element, this.connection, this.remembered);
    }

    @Test
    void theDeletedClientsAreListed() {
        plans(delete(ResourceType.CLIENT, "shop"), delete(ResourceType.CLIENT, "admin"));

        assertThat(warning(client("web")))
                .isEqualTo("At the next import, these clients of Keycloak missing from the files will be deleted on"
                        + " dev (2): shop, admin. Confirm?");
    }

    @Test
    void groupsAreListedByPath() {
        plans(delete(ResourceType.GROUP, "/staff/ops"));

        assertThat(warning(group("/staff")))
                .isEqualTo("At the next import, these groups of Keycloak missing from the files will be deleted on"
                        + " dev (1): /staff/ops. Confirm?");
    }

    @Test
    void onlyTheDeletionsOfTheDeclaredTypeAreListed() {
        plans(
                delete(ResourceType.CLIENT, "shop"),
                delete(ResourceType.GROUP, "/staff"),
                new PlannedChange(ResourceType.CLIENT, "created", Kind.CREATE, null, null),
                new PlannedChange(ResourceType.CLIENT, "updated", Kind.UPDATE, null, null));

        assertThat(warning(client("web"))).contains("(1): shop.").doesNotContain("/staff", "created", "updated");
    }

    @Test
    void aClientRoleListsOnlyTheRolesOfItsClient() {
        plans(
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("shop", "buyer")),
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("admin", "root")),
                delete(ResourceType.REALM_ROLE, RoleKey.realm("staff")),
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("shop", "seller")));

        assertThat(warning(clientRole("shop", "viewer")))
                .isEqualTo("At the next import, these roles of client 'shop' of Keycloak missing from the files will"
                        + " be deleted on dev (2): buyer, seller. Confirm?");
    }

    @Test
    void realmRolesIgnoreClientRoles() {
        plans(
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("shop", "buyer")),
                delete(ResourceType.REALM_ROLE, RoleKey.realm("staff")));

        assertThat(warning(realmRole("viewer")))
                .isEqualTo("At the next import, these realm roles of Keycloak missing from the files will be deleted"
                        + " on dev (1): staff. Confirm?");
    }

    @Test
    void tenNamesThenAndMore() {
        List<PlannedChange> changes = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            changes.add(delete(ResourceType.CLIENT, "c" + i));
        }
        plans(changes.toArray(PlannedChange[]::new));

        assertThat(warning(client("web")))
                .isEqualTo("At the next import, these clients of Keycloak missing from the files will be deleted on"
                        + " dev (13): c1, c2, c3, c4, c5, c6, c7, c8, c9, c10 and 3 more. Confirm?");
    }

    @Test
    void noDeletionSaysSo() {
        plans(new PlannedChange(ResourceType.CLIENT, "shop", Kind.CREATE, null, null));

        assertThat(warning(client("web")))
                .isEqualTo("No clients of Keycloak would be deleted on dev now, but at each import the clients"
                        + " missing from the files will be. Confirm?");
    }

    @Test
    void theDeletionsOfEveryRealmOfTheReportAreRead() {
        Map<String, List<PlannedChange>> byRealm = new java.util.LinkedHashMap<>();
        byRealm.put("demo", List.of(delete(ResourceType.CLIENT, "a")));
        byRealm.put("other", List.of(delete(ResourceType.CLIENT, "b")));
        ImportReport result = new ImportReport(byRealm);
        this.report = () -> result;

        assertThat(warning(client("web")))
                .isEqualTo("At the next import, these clients of Keycloak missing from the files will be deleted on"
                        + " dev (2): a, b. Confirm?");
    }

    private static String generic(final ElementDefinition element, final String reason) {
        return FirstDeclarationException.warning(element) + " (dry run on dev not possible: " + reason + ")";
    }

    @Test
    void aMissingVariableGivesTheGenericWarningWithItsName() {
        this.definitions.sources = Map.of("00-realm.yaml", "url: {{shopUrl}}");

        assertThat(warning(client("web"))).isEqualTo(generic(client("web"), "variable 'shopUrl' has no value"));
        assertThat(this.simulated).isEmpty();
    }

    @Test
    void anUnreachableKeycloakGivesTheGenericWarning() {
        fails(new KeycloakUnreachableException(DEV, "Connection refused: secret-detail", null));

        assertThat(warning(group("/staff"))).isEqualTo(generic(group("/staff"), "Keycloak unreachable"));
    }

    @Test
    void aRefusalGivesTheGenericWarning() {
        fails(KeycloakAuthenticationException.refusedOperation(DEV, "HTTP 403", null));

        assertThat(warning(realmRole("viewer")))
                .isEqualTo(generic(realmRole("viewer"), "Keycloak refused the dry run"));
    }

    @Test
    void anInvalidFileGivesTheGenericWarningWithoutItsMessage() {
        fails(new InvalidImportException("bad value hunter2 at line 3", null));

        String text = warning(client("web"));

        assertThat(text).isEqualTo(generic(client("web"), "a file is invalid")).doesNotContain("hunter2");
    }

    @Test
    void anApplicationFailureGivesTheGenericWarningWithoutItsMessage() {
        fails(new MissingAnswerException("marker-value"));

        String text = warning(client("web"));

        assertThat(text).isEqualTo(generic(client("web"), "the dry run failed")).doesNotContain("marker-value");
    }

    @Test
    void anyOtherFailureGivesTheGenericWarningWithoutItsMessage() {
        fails(new IllegalStateException("marker-value"));

        String text = warning(client("web"));

        assertThat(text).isEqualTo(generic(client("web"), "the dry run failed")).doesNotContain("marker-value");
    }

    @Test
    void informationHasTheGenericPathToo() {
        fails(new KeycloakUnreachableException(DEV, "down", null));

        assertThat(deletions().information("demo", client("web"), this.connection, this.remembered))
                .isEqualTo(generic(client("web"), "Keycloak unreachable"));
    }

    @Test
    void aTemplatedClientIdIsComparedRenderedFromMemory() {
        this.definitions.sources = Map.of("00-realm.yaml", "id: {{env}}-shop");
        this.remembered.remember("env", "prod");
        plans(
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("prod-shop", "buyer")),
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("dev-shop", "other")));

        assertThat(warning(clientRole("{{env}}-shop", "viewer")))
                .isEqualTo("At the next import, these roles of client '{{env}}-shop' of Keycloak missing from the"
                        + " files will be deleted on dev (1): buyer. Confirm?");
    }

    @Test
    void aTemplatedClientIdIsComparedRenderedWithItsDefault() {
        this.definitions.sources = Map.of("00-realm.yaml", "id: {{env:dev}}-shop");
        plans(
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("dev-shop", "buyer")),
                delete(ResourceType.CLIENT_ROLE, RoleKey.client("prod-shop", "other")));

        assertThat(warning(clientRole("{{env:dev}}-shop", "viewer"))).contains("(1): buyer.");
    }

    @Test
    void noQuestionIsAsked() {
        this.definitions.sources = Map.of("00-realm.yaml", "url: {{shopUrl}}");
        this.environment.put("KCC_VAR_shopUrl", "https://shop.example.org");
        plans(delete(ResourceType.CLIENT, "shop"));

        // the Questions double fails when called
        assertThat(warning(client("web"))).contains("(1): shop");
        assertThat(this.definitions.importedWith).containsEntry("shopUrl", "https://shop.example.org");
    }

    @Test
    void theValuesAreNotRemembered() {
        this.definitions.sources = Map.of("00-realm.yaml", "url: {{shopUrl}}");
        this.environment.put("KCC_VAR_shopUrl", "https://shop.example.org");
        plans();

        warning(client("web"));

        assertThat(this.remembered.get("shopUrl")).isEmpty();
    }

    @Test
    void theRememberedValueIsUsed() {
        this.definitions.sources = Map.of("00-realm.yaml", "url: {{shopUrl}}");
        this.remembered.remember("shopUrl", "from-memory");
        plans();

        warning(client("web"));

        assertThat(this.definitions.importedWith).containsEntry("shopUrl", "from-memory");
    }

    @Test
    void theSimulationIsGivenTheElement() {
        plans();

        warning(client("web"));

        assertThat(this.definitions.importedWithElement).containsExactly(client("web"));
        assertThat(this.simulated).hasSize(1);
    }

    @Test
    void informationHasNoQuestion() {
        plans(delete(ResourceType.CLIENT, "shop"));

        assertThat(deletions().information("demo", client("web"), this.connection, this.remembered))
                .isEqualTo("At the next import, these clients of Keycloak missing from the files will be deleted on"
                        + " dev (1): shop.");
    }

    @Test
    void theOfflineWarningIsTheCurrentOne() {
        assertThat(DeclarationWarning.OFFLINE.of("demo", client("web")))
                .isEqualTo(FirstDeclarationException.warning(client("web")));
    }

    @Test
    void theExceptionCarriesAGivenWarning() {
        assertThat(new FirstDeclarationException(client("web"), "Precise text. Confirm?").getMessage())
                .isEqualTo("Precise text. Confirm? In a script, add --declare-type.");
    }

    @Test
    void aVariableNewInTheElementTakesTheEnvironment() {
        this.definitions.sourcesWith = Map.of("20-clients.yaml", "clients:\n  - clientId: \"{{appId}}\"");
        this.environment.put("KCC_VAR_appId", "prod-app");
        plans(delete(ResourceType.CLIENT, "legacy"));

        assertThat(warning(client("{{appId}}"))).contains("(1): legacy.");
        assertThat(this.definitions.importedWith).containsEntry("appId", "prod-app");
    }

    @Test
    void aVariableNewInTheElementTakesTheEnvironmentOverItsDefault() {
        this.definitions.sourcesWith = Map.of("20-clients.yaml", "clients:\n  - clientId: \"{{appId:dev-app}}\"");
        this.environment.put("KCC_VAR_appId", "prod-app");
        plans();

        warning(client("{{appId:dev-app}}"));

        // rendered as the dry run renders it: the environment wins over the default
        assertThat(this.definitions.importedWith).containsEntry("appId", "prod-app");
    }

    @Test
    void aVariableNewInTheElementWithoutValueIsNamed() {
        this.definitions.sourcesWith = Map.of("20-clients.yaml", "clients:\n  - clientId: \"{{appId}}\"");

        assertThat(warning(client("{{appId}}")))
                .isEqualTo(generic(client("{{appId}}"), "variable 'appId' has no value"));
        assertThat(this.simulated).isEmpty();
    }
}
