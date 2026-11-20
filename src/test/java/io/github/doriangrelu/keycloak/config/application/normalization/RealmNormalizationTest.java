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

package io.github.doriangrelu.keycloak.config.application.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.normalization.NormalizedRealm.MissingResource;
import io.github.doriangrelu.keycloak.config.application.normalization.port.ReferenceRealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RealmNormalizationTest {

    private static final String REALM = "realm";

    /** References in memory: the realm given by the test, and clients with an empty name. */
    private static final class References implements ReferenceRealmGateway {
        private final DesiredState realm;

        References(final DesiredState realm) {
            this.realm = realm;
        }

        @Override
        public DesiredState referenceRealm(final String keycloakVersion, final String realm) {
            return this.realm;
        }

        @Override
        public Client referenceClient(final String keycloakVersion, final String clientId) {
            return client(clientId, "", Map.of("default", "true"));
        }
    }

    private static NormalizedRealm normalize(final DesiredState exported, final DesiredState reference) {
        return new RealmNormalization(new References(reference)).normalize(REALM, "26.5.3", exported);
    }

    private static DesiredState state() {
        return new DesiredState()
                .declare(ResourceType.REALM_SETTINGS, List.of(new RealmSettings(REALM, UnmodeledAttributes.none())));
    }

    private static Client client(final String clientId, final String name, final Map<String, String> attributes) {
        return new Client(clientId, name, attributes, null, null, null, null, null, false, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource<?>> List<R> kept(final NormalizedRealm normalized, final ResourceType type) {
        return normalized
                .resources()
                .resources(type)
                .map(list -> (List<R>) list)
                .orElse(List.of());
    }

    @Test
    void clientsAreComparedWithTheirReferenceAndKeepOnlyTheirDifferingValues() {
        DesiredState reference = state().declare(
                        ResourceType.CLIENT,
                        List.of(
                                client("account", "Account", Map.of("a", "1")),
                                client("broker", "Broker", Map.of()),
                                client("deleted", "Deleted", Map.of())));
        DesiredState exported = state().declare(
                        ResourceType.CLIENT,
                        List.of(
                                client("account", "Account", Map.of("a", "2")),
                                client("broker", "Broker", Map.of()),
                                client("app", "App", Map.of("default", "true", "own", "x"))))
                .declare(
                        ResourceType.CLIENT_FLOW_BINDING,
                        List.of(new ClientFlowBindings("app", null, Map.of("browser", "mine"))));

        NormalizedRealm normalized = normalize(exported, reference);

        assertThat(kept(normalized, ResourceType.CLIENT))
                .containsExactly(client("account", null, Map.of("a", "2")), client("app", "App", Map.of("own", "x")));
        assertThat(kept(normalized, ResourceType.CLIENT_FLOW_BINDING))
                .containsExactly(new ClientFlowBindings("app", null, Map.of("browser", "mine")));
        assertThat(normalized.missing()).containsExactly(new MissingResource(ResourceType.CLIENT, "deleted"));
    }

    @Test
    void aChangedGroupIsKeptWithItsWholeTree() {
        Group staff = new Group("/staff", null, Set.of(), null, Set.of("ops"), null);
        Group ops = new Group("/staff/ops", Map.of("site", List.of("paris")), null, null, Set.of(), null);
        Group other = new Group("/other", null, null, null, Set.of(), null);
        DesiredState reference = state().declare(
                        ResourceType.GROUP,
                        List.of(staff, new Group("/staff/ops", null, null, null, Set.of(), null), other));
        DesiredState exported = state().declare(ResourceType.GROUP, List.of(staff, ops, other));

        assertThat(kept(normalize(exported, reference), ResourceType.GROUP)).containsExactly(staff, ops);
    }

    @Test
    void aChangedSubComponentIsKeptWithItsWholeTree() {
        Component.Key ldapKey = new Component.Key(null, "storage", null, "ldap");
        Component ldap = new Component(ldapKey, null, "ldap", Map.of());
        Component.Key emailKey = new Component.Key(ldapKey, "mapper", null, "email");
        Component.Key nameKey = new Component.Key(ldapKey, "mapper", null, "name");
        Component name = new Component(nameKey, null, "attribute", Map.of());
        DesiredState reference = state().declare(ResourceType.COMPONENT, List.of(ldap))
                .declare(
                        ResourceType.SUB_COMPONENT,
                        List.of(new Component(emailKey, null, "attribute", Map.of()), name));
        Component email = new Component(emailKey, null, "attribute", Map.of("read.only", List.of("true")));
        DesiredState exported = state().declare(ResourceType.COMPONENT, List.of(ldap))
                .declare(ResourceType.SUB_COMPONENT, List.of(email, name));

        NormalizedRealm normalized = normalize(exported, reference);

        assertThat(kept(normalized, ResourceType.COMPONENT)).containsExactly(ldap);
        assertThat(kept(normalized, ResourceType.SUB_COMPONENT)).containsExactly(email, name);
    }

    @Test
    void aChangedFlowIsKeptWithTheConfigsItUsesAndAChangedConfigOfTheReferenceIsKept() {
        AuthenticatorConfig otp = new AuthenticatorConfig("otp", Map.of("mode", "strict"), false);
        AuthenticatorConfig review = new AuthenticatorConfig("review", Map.of("update", "missing"), false);
        AuthenticatorConfig orphan = new AuthenticatorConfig("orphan", Map.of(), false);
        AuthenticationFlow browser = new AuthenticationFlow(
                "browser", null, true, new UnmodeledAttributes(Map.of("description", "browser")), null, null);
        AuthenticationFlow mine = new AuthenticationFlow(
                "mine", null, false, new UnmodeledAttributes(Map.of("description", "mine")), null, Map.of("otp", otp));
        DesiredState reference = state().declare(ResourceType.AUTHENTICATION_FLOW, List.of(browser))
                .declare(ResourceType.AUTHENTICATOR_CONFIG, List.of(review));
        AuthenticatorConfig changedReview = new AuthenticatorConfig("review", Map.of("update", "on"), false);
        DesiredState exported = state().declare(ResourceType.AUTHENTICATION_FLOW, List.of(browser, mine))
                .declare(ResourceType.AUTHENTICATOR_CONFIG, List.of(changedReview, otp, orphan));

        NormalizedRealm normalized = normalize(exported, reference);

        assertThat(kept(normalized, ResourceType.AUTHENTICATION_FLOW)).containsExactly(mine);
        assertThat(kept(normalized, ResourceType.AUTHENTICATOR_CONFIG)).containsExactly(changedReview, otp);
    }

    @Test
    void aRoleWhoseCompositesChangedIsKeptWithThem() {
        Role admin = new Role(new RoleKey(null, "admin"), null, null, null);
        Role user = new Role(new RoleKey(null, "user"), null, null, null);
        RoleComposites composites = new RoleComposites(new RoleKey(null, "admin"), Set.of("user"), Map.of());
        DesiredState reference = state().declare(ResourceType.REALM_ROLE, List.of(admin, user));
        DesiredState exported = state().declare(ResourceType.REALM_ROLE, List.of(admin, user))
                .declare(ResourceType.ROLE_COMPOSITE, List.of(composites));

        NormalizedRealm normalized = normalize(exported, reference);

        assertThat(kept(normalized, ResourceType.REALM_ROLE)).containsExactly(admin);
        assertThat(kept(normalized, ResourceType.ROLE_COMPOSITE)).containsExactly(composites);
    }

    @Test
    void realmSettingsAreAlwaysKept() {
        assertThat(kept(normalize(state(), state()), ResourceType.REALM_SETTINGS))
                .containsExactly(new RealmSettings(REALM, UnmodeledAttributes.none()));
    }
}
