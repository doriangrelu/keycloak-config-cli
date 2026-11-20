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

package io.github.doriangrelu.keycloak.config.domain.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The resources Keycloak creates in a realm, which the cleanup never deletes (ADR-007). */
class TechnicalResourcesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "account",
                "account-console",
                "admin-cli",
                "broker",
                "realm-management",
                "security-admin-console",
                "admin-permissions"
            })
    void builtInClientsWhateverTheirDisplayName(final String clientId) {
        Client renamed = new Client(clientId, "Renamed", null, null, null, null, null, null, null, null, null);

        assertThat(renamed.isOwnedByKeycloak("realm")).isTrue();
    }

    @Test
    void realmClientsOfTheMasterRealmOnly() {
        Client realmClient = new Client("other-realm", null, null, null, null, null, null, null, null, null, null);

        assertThat(realmClient.isOwnedByKeycloak("master")).isTrue();
        assertThat(realmClient.isOwnedByKeycloak("realm")).isFalse();
        assertThat(new Client("app", "App", null, null, null, null, null, null, null, null, null)
                        .isOwnedByKeycloak("realm"))
                .isFalse();
    }

    @Test
    void realmRolesAndRolesOfKeycloakClients() {
        assertThat(role(null, "offline_access").isOwnedByKeycloak("realm")).isTrue();
        assertThat(role(null, "uma_authorization").isOwnedByKeycloak("realm")).isTrue();
        assertThat(role(null, "default-roles-realm").isOwnedByKeycloak("realm")).isTrue();
        assertThat(role("realm-management", "view-users").isOwnedByKeycloak("realm"))
                .isTrue();
        assertThat(role("other-realm", "view-users").isOwnedByKeycloak("realm")).isTrue();
        assertThat(role(null, "admin").isOwnedByKeycloak("realm")).isFalse();
        assertThat(role(null, "default-roles-other").isOwnedByKeycloak("realm")).isFalse();
        assertThat(role("app", "offline_access").isOwnedByKeycloak("realm")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "email", "roles", "web-origins", "offline_access", "basic", "service_account"})
    void builtInClientScopesEvenOnceTheRealmNoLongerListsThem(final String name) {
        assertThat(scope(name, false).isOwnedByKeycloak()).isTrue();
    }

    @Test
    void defaultScopesOfTheRealmAndNoOthers() {
        assertThat(scope("custom", true).isOwnedByKeycloak()).isTrue();
        assertThat(scope("custom", false).isOwnedByKeycloak()).isFalse();
    }

    @Test
    void scopeMappingsKeycloakCreates() {
        assertThat(new ScopeMapping(ScopeMapping.Key.ofClientScope("offline_access", null), Set.of())
                        .isOwnedByKeycloak())
                .isTrue();
        assertThat(new ScopeMapping(ScopeMapping.Key.ofClient("account-console", "account"), Set.of())
                        .isOwnedByKeycloak())
                .isTrue();
        assertThat(new ScopeMapping(ScopeMapping.Key.ofClient("app", null), Set.of()).isOwnedByKeycloak())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIGURE_TOTP", "terms_and_conditions", "TERMS_AND_CONDITIONS", "delete_account"})
    void requiredActionsKeycloakRegisters(final String alias) {
        assertThat(action(alias).isOwnedByKeycloak()).isTrue();
    }

    @Test
    void customRequiredActions() {
        assertThat(action("custom-action").isOwnedByKeycloak()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "org.keycloak.keys.KeyProvider",
                "org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy",
                "org.keycloak.userprofile.UserProfileProvider"
            })
    void keysClientRegistrationPoliciesAndUserProfile(final String providerType) {
        assertThat(component(new Component.Key(null, providerType, null, "name"))
                        .isOwnedByKeycloak())
                .isTrue();
    }

    @Test
    void userStoragesAndTheirMappers() {
        Component.Key ldap = new Component.Key(null, "org.keycloak.storage.UserStorageProvider", null, "ldap");

        assertThat(component(ldap).isOwnedByKeycloak()).isFalse();
        assertThat(component(new Component.Key(ldap, "org.keycloak.keys.KeyProvider", null, "child"))
                        .isOwnedByKeycloak())
                .isFalse();
    }

    private static Role role(final String clientId, final String name) {
        return new Role(clientId == null ? new RoleKey(null, name) : RoleKey.client(clientId, name), null, null, null);
    }

    private static ClientScope scope(final String name, final boolean realmDefault) {
        return new ClientScope(name, null, null, null, null, realmDefault, null);
    }

    private static RequiredAction action(final String alias) {
        return new RequiredAction(alias, alias, alias, true, false, 0, Map.of(), null);
    }

    private static Component component(final Component.Key key) {
        return new Component(key, null, "provider", Map.of());
    }
}
