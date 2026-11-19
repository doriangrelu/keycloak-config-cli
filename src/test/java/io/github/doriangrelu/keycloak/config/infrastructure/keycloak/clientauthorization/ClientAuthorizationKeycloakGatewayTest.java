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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.application.clientauthorization.UnknownAuthorizationReferenceException;
import io.github.doriangrelu.keycloak.config.application.clientauthorization.port.ClientAuthorizationGateway.Deletions;
import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientAuthorizationApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.mockito.ArgumentCaptor;

class ClientAuthorizationKeycloakGatewayTest {

    private static final String REALM = "realm";
    private static final Deletions ALL = new Deletions(true, Optional.empty(), true, true);

    private final UnmodeledAttributesConverter converter = new UnmodeledAttributesConverter();
    private ClientAuthorizationApi authorizationApi;
    private ClientApi clientApi;
    private ClientAuthorizationKeycloakGateway gateway;

    @BeforeEach
    void setUp() {
        this.authorizationApi = mock(ClientAuthorizationApi.class);
        this.clientApi = mock(ClientApi.class);
        this.gateway = new ClientAuthorizationKeycloakGateway(
                this.authorizationApi,
                this.clientApi,
                mock(GroupApi.class),
                mock(RoleApi.class),
                mock(IdentityProviderApi.class),
                this.converter);
    }

    private void givenClient(String clientId, ResourceServerRepresentation existing) {
        ClientRepresentation client = new ClientRepresentation();
        client.setId(clientId + "-uuid");
        client.setClientId(clientId);
        when(this.clientApi.findByClientId(REALM, clientId)).thenReturn(Optional.of(client));
        when(this.authorizationApi.exportSettings(REALM, clientId + "-uuid")).thenReturn(existing);
    }

    private static ResourceServerRepresentation settings(
            List<ResourceRepresentation> resources,
            List<PolicyRepresentation> policies,
            List<ScopeRepresentation> scopes) {
        ResourceServerRepresentation settings = new ResourceServerRepresentation();
        settings.setResources(new ArrayList<>(resources));
        settings.setPolicies(new ArrayList<>(policies));
        settings.setScopes(new ArrayList<>(scopes));
        return settings;
    }

    private static ResourceRepresentation resource(String name) {
        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName(name);
        return resource;
    }

    private static PolicyRepresentation policy(String name, Map<String, String> config) {
        PolicyRepresentation policy = new PolicyRepresentation();
        policy.setName(name);
        policy.setType("scope");
        policy.setConfig(new HashMap<>(config));
        return policy;
    }

    private ClientAuthorization declared(String clientId, ResourceServerRepresentation settings) {
        return new ClientAuthorization(clientId, null, this.converter.extract(settings, Set.of()));
    }

    private static KeycloakAccessException failure(int status) {
        KeycloakFailure failure = switch (status) {
            case 400 -> KeycloakFailure.REJECTED;
            case 404 -> KeycloakFailure.NOT_FOUND;
            default -> KeycloakFailure.UNEXPECTED;
        };
        return new KeycloakAccessException(failure, status, "HTTP " + status, null);
    }

    @Test
    void createsTheDeclaredResourcesScopesAndPolicies() {
        givenClient("app", settings(List.of(), List.of(), List.of()));
        ScopeRepresentation scope = new ScopeRepresentation("read");

        this.gateway.apply(
                REALM,
                declared("app", settings(List.of(resource("docs")), List.of(policy("only", Map.of())), List.of(scope))),
                ALL);

        verify(this.authorizationApi).createResource(eq(REALM), eq("app-uuid"), any());
        verify(this.authorizationApi).createScope(eq(REALM), eq("app-uuid"), any());
        verify(this.authorizationApi).createPolicy(eq(REALM), eq("app-uuid"), any());
    }

    @Test
    void goesOnWhenTheAuthorizationApiIsNotAvailable() {
        givenClient("app", settings(List.of(), List.of(), List.of()));
        doThrow(failure(501)).when(this.authorizationApi).createResource(anyString(), anyString(), any());
        doThrow(failure(404)).when(this.authorizationApi).createPolicy(anyString(), anyString(), any());

        this.gateway.apply(
                REALM,
                declared("app", settings(List.of(resource("docs")), List.of(policy("only", Map.of())), List.of())),
                ALL);

        verify(this.authorizationApi).createPolicy(eq(REALM), eq("app-uuid"), any());
    }

    @Test
    void stopsOnAServerError() {
        givenClient("app", settings(List.of(), List.of(), List.of()));
        doThrow(failure(500)).when(this.authorizationApi).createResource(anyString(), anyString(), any());

        assertThatThrownBy(() -> this.gateway.apply(
                        REALM, declared("app", settings(List.of(resource("docs")), List.of(), List.of())), ALL))
                .isInstanceOf(KeycloakAccessException.class);
    }

    @Test
    void skipsTheAdminPermissionsClientWithTheFineGrainedPermissionsOfVersion2() {
        ClientRepresentation client = new ClientRepresentation();
        client.setId("uuid");
        client.setClientId("admin-permissions");
        when(this.clientApi.findByClientId(REALM, "admin-permissions")).thenReturn(Optional.of(client));
        when(this.authorizationApi.isFgapV2Active()).thenReturn(true);

        this.gateway.apply(
                REALM, declared("admin-permissions", settings(List.of(resource("Users")), List.of(), List.of())), ALL);

        verify(this.authorizationApi).isFgapV2Active();
        verifyNoMoreInteractions(this.authorizationApi);
    }

    @Test
    void aRefusedSettingsUpdateOnlyGoesOnForTheAdminPermissionsClient() {
        ResourceServerRepresentation declared = settings(List.of(), List.of(), List.of());
        declared.setDecisionStrategy(org.keycloak.representations.idm.authorization.DecisionStrategy.AFFIRMATIVE);
        givenClient("app", settings(List.of(), List.of(), List.of()));
        doThrow(failure(400)).when(this.authorizationApi).updateSettings(anyString(), anyString(), any());

        assertThatThrownBy(() -> this.gateway.apply(REALM, declared("app", declared), ALL))
                .isInstanceOf(KeycloakAccessException.class);

        givenClient("admin-permissions", settings(List.of(), List.of(), List.of()));
        this.gateway.apply(REALM, declared("admin-permissions", declared), ALL);
    }

    @Test
    void resolvesAClientPlaceholderOfRealmManagementByVersion() {
        ClientRepresentation target = new ClientRepresentation();
        target.setId("target-uuid");
        when(this.clientApi.findByClientId(REALM, "target")).thenReturn(Optional.of(target));
        when(this.authorizationApi.isClientPermissionEnabled(REALM, "target-uuid"))
                .thenReturn(false);
        givenClient("realm-management", settings(List.of(), List.of(), List.of()));
        ArgumentCaptor<ResourceRepresentation> created = ArgumentCaptor.forClass(ResourceRepresentation.class);

        this.gateway.apply(
                REALM,
                declared(
                        "realm-management",
                        settings(List.of(resource("client.resource.$target")), List.of(), List.of())),
                ALL);

        verify(this.authorizationApi).enableClientPermission(REALM, "target-uuid");
        verify(this.authorizationApi).createResource(eq(REALM), eq("realm-management-uuid"), created.capture());
        assertThat(created.getValue().getName()).isEqualTo("client.resource.target-uuid");

        when(this.authorizationApi.isFgapV2Active()).thenReturn(true);
        this.gateway.apply(
                REALM,
                declared(
                        "realm-management",
                        settings(List.of(resource("client.resource.$target")), List.of(), List.of())),
                ALL);
        verify(this.authorizationApi, org.mockito.Mockito.times(2))
                .createResource(eq(REALM), eq("realm-management-uuid"), created.capture());
        assertThat(created.getValue().getName()).isEqualTo("target-uuid");
    }

    @Test
    void resolvesABarePlaceholderOfAPolicyWithItsDefaultResourceType() {
        ClientRepresentation target = new ClientRepresentation();
        target.setId("target-uuid");
        when(this.clientApi.findByClientId(REALM, "target")).thenReturn(Optional.of(target));
        when(this.authorizationApi.isFgapV2Active()).thenReturn(true);
        givenClient("app", settings(List.of(), List.of(), List.of()));
        ArgumentCaptor<PolicyRepresentation> created = ArgumentCaptor.forClass(PolicyRepresentation.class);

        this.gateway.apply(
                REALM,
                declared(
                        "app",
                        settings(
                                List.of(),
                                List.of(policy(
                                        "view target",
                                        Map.of("defaultResourceType", "Clients", "resources", "[\"$target\"]"))),
                                List.of())),
                ALL);

        verify(this.authorizationApi).createPolicy(eq(REALM), eq("app-uuid"), created.capture());
        assertThat(created.getValue().getConfig()).containsEntry("resources", "[\"target-uuid\"]");
    }

    @Test
    void rejectsAPlaceholderOfAnUnsupportedType() {
        givenClient("realm-management", settings(List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> this.gateway.apply(
                        REALM,
                        declared(
                                "realm-management",
                                settings(List.of(resource("unknown.resource.$x")), List.of(), List.of())),
                        ALL))
                .isInstanceOf(UnknownAuthorizationReferenceException.class)
                .hasMessage("Cannot resolve 'unknown.resource.$x' in realm 'realm', the type 'unknown' is not"
                        + " supported by keycloak-config-cli.");
    }

    @Test
    void withTheRemoteStateOnlyDeletesTheResourcesItCreatedAndTheDefaultOne() {
        givenClient(
                "app",
                settings(
                        List.of(resource("created"), resource("foreign"), resource("Default Resource")),
                        List.of(),
                        List.of()));

        this.gateway.apply(
                REALM,
                declared("app", settings(List.of(), List.of(), List.of())),
                new Deletions(true, Optional.of(Set.of("created")), false, false));

        verify(this.authorizationApi).deleteResource(REALM, "app-uuid", "created");
        verify(this.authorizationApi).deleteResource(REALM, "app-uuid", "Default Resource");
        verify(this.authorizationApi, never()).deleteResource(REALM, "app-uuid", "foreign");
    }
}
