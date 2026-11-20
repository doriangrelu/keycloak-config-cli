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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.exception.InvalidImportException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;

/**
 * The guarantees of the cleanup (ADR-007): what the files no longer declare is deleted, except
 * the users, the resources of Keycloak and the resources protected by equality or by pattern.
 */
class ImportCleanupGuaranteesIT extends AbstractImportIT {

    private static final String REALM_NAME = "cleanupGuarantees";
    private static final String TYPED_REALM_NAME = "cleanupGuaranteesTyped";

    ImportCleanupGuaranteesIT() {
        this.resourcePath = "import-files/cleanup-guarantees";
    }

    @Test
    void deletesWhatTheFilesNoLongerDeclareExceptUsersTechnicalAndProtectedResources() throws IOException {
        doImport("00_create_realm_with_resources.json");
        RealmRepresentation before = export();

        doImport("01_remove_resources.json");
        RealmRepresentation after = export();

        // Declared: kept
        assertThat(names(after.getClients(), ClientRepresentation::getClientId)).contains("app-app");
        // Protected by equality or by pattern: kept, although no longer declared
        assertThat(names(after.getClients(), ClientRepresentation::getClientId))
                .contains("legacy-app", "team-billing")
                .doesNotContain("orphan-app");
        assertThat(names(after.getRoles().getRealm(), RoleRepresentation::getName))
                .contains("app-role", "legacy-role", "team-role")
                .doesNotContain("orphan-role");
        assertThat(names(after.getGroups(), GroupRepresentation::getName))
                .contains("app-group", "legacy-group", "team-group")
                .doesNotContain("orphan-group");
        assertThat(names(after.getClientScopes(), ClientScopeRepresentation::getName))
                .contains("app-scope", "legacy-scope", "team-scope")
                .doesNotContain("orphan-scope");
        assertThat(names(after.getIdentityProviders(), IdentityProviderRepresentation::getAlias))
                .contains("app-idp", "legacy-idp", "team-idp")
                .doesNotContain("orphan-idp");

        // Users: never deleted
        assertThat(keycloakProvider.getInstance().realm(REALM_NAME).users().search("user-a", true))
                .hasSize(1);

        // Technical resources of Keycloak: all still there
        assertThat(names(after.getClients(), ClientRepresentation::getClientId))
                .contains(
                        "account",
                        "account-console",
                        "admin-cli",
                        "broker",
                        "realm-management",
                        "security-admin-console");
        assertThat(names(after.getRoles().getRealm(), RoleRepresentation::getName))
                .contains("offline_access", "uma_authorization", "default-roles-" + REALM_NAME.toLowerCase());
        assertThat(after.getRoles().getClient().get("realm-management"))
                .hasSameSizeAs(before.getRoles().getClient().get("realm-management"));
        assertThat(names(after.getClientScopes(), ClientScopeRepresentation::getName))
                .containsAll(technical(names(before.getClientScopes(), ClientScopeRepresentation::getName)));
        assertThat(names(after.getAuthenticationFlows(), flow -> flow.getAlias()))
                .containsAll(names(before.getAuthenticationFlows(), flow -> flow.getAlias()));
        assertThat(names(after.getRequiredActions(), RequiredActionProviderRepresentation::getAlias))
                .containsAll(names(before.getRequiredActions(), RequiredActionProviderRepresentation::getAlias));
        assertThat(componentNames(after)).containsAll(componentNames(before));
        assertThat(after.getScopeMappings())
                .extracting(ScopeMappingRepresentation::getClientScope)
                .contains("offline_access");
    }

    @Test
    void aRuleWithATypeOnlyProtectsThatTypeAndAnInvalidPatternChangesNothing() throws IOException {
        doImport("02_create_realm_with_shared_names.json");

        doImport("03_protect_clients_only.json");
        RealmRepresentation after =
                keycloakProvider.getInstance().realm(TYPED_REALM_NAME).partialExport(true, true);

        // The rule is restricted to the clients: the role and the scope with the same name are deleted
        assertThat(names(after.getClients(), ClientRepresentation::getClientId)).contains("shared-name");
        assertThat(names(after.getRoles().getRealm(), RoleRepresentation::getName))
                .doesNotContain("shared-name");
        assertThat(names(after.getClientScopes(), ClientScopeRepresentation::getName))
                .doesNotContain("shared-name");

        // An invalid regular expression fails when the file is read, before anything is changed in Keycloak
        assertThatThrownBy(() -> doImport("04_invalid_pattern.json"))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("shared-(");
        RealmRepresentation unchanged =
                keycloakProvider.getInstance().realm(TYPED_REALM_NAME).toRepresentation();
        assertThat(unchanged.getDisplayName()).isNull();
        assertThat(names(
                        keycloakProvider
                                .getInstance()
                                .realm(TYPED_REALM_NAME)
                                .clients()
                                .findAll(),
                        ClientRepresentation::getClientId))
                .contains("shared-name");
    }

    private RealmRepresentation export() {
        return keycloakProvider.getInstance().realm(REALM_NAME).partialExport(true, true);
    }

    private static <T> Set<String> names(final Collection<T> resources, final Function<T, String> name) {
        return resources == null ? Set.of() : resources.stream().map(name).collect(Collectors.toSet());
    }

    /** The scopes of the realm before the test resources. */
    private static Set<String> technical(final Set<String> scopes) {
        return scopes.stream()
                .filter(scope -> !List.of("app-scope", "legacy-scope", "team-scope", "orphan-scope")
                        .contains(scope))
                .collect(Collectors.toSet());
    }

    private static Set<String> componentNames(final RealmRepresentation realm) {
        if (realm.getComponents() == null) {
            return Set.of();
        }
        return realm.getComponents().entrySet().stream()
                .flatMap(type -> type.getValue().stream()
                        .map(ComponentExportRepresentation::getName)
                        .map(name -> type.getKey() + "/" + name))
                .collect(Collectors.toSet());
    }
}
