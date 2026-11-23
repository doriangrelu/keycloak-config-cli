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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.domain.user.model.Credential;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

class UserKeycloakGatewayTest {

    private static final String REALM = "test";

    private UserApi userApi;
    private UserKeycloakGateway gateway;
    private final List<UserRepresentation> written = new ArrayList<>();

    @BeforeEach
    void setUp() {
        this.userApi = mock(UserApi.class);
        this.gateway = new UserKeycloakGateway(
                this.userApi,
                mock(RoleApi.class),
                mock(GroupApi.class),
                mock(ClientApi.class),
                new UserKeycloakMapper(new UnmodeledAttributesConverter()),
                new KeycloakImportOptions(false, false, false));
        UserRepresentation existing = new UserRepresentation();
        existing.setId("id");
        existing.setUsername("u");
        when(this.userApi.findByUsername(REALM, "u")).thenReturn(Optional.of(existing));
    }

    private static User user(String lastName, Credential... credentials) {
        return new User(
                "u",
                null,
                null,
                null,
                lastName,
                null,
                null,
                Map.of(),
                List.of(credentials),
                Set.of(),
                Map.of(),
                Set.of(),
                null);
    }

    private void refuse(String message) {
        doThrow(new KeycloakAccessException(KeycloakFailure.REJECTED, message))
                .doAnswer(invocation -> this.written.add(invocation.getArgument(2)))
                .when(this.userApi)
                .updateUser(eq(REALM), eq("id"), any());
    }

    @Test
    void aPasswordRefusedByThePasswordHistoryIsLeftOut() {
        refuse("{\"errorMessage\":\"invalidPasswordHistoryMessage\"}");

        this.gateway.update(REALM, user("old"), user("new", new Credential(Credential.PASSWORD, null, null)));

        verify(this.userApi, times(2)).updateUser(eq(REALM), eq("id"), any());
        assertThat(this.written).singleElement().satisfies(sent -> {
            assertThat(sent.getLastName()).isEqualTo("new");
            assertThat(sent.getCredentials()).isNull();
        });
    }

    @Test
    void otherRefusalsAreReported() {
        refuse("{\"errorMessage\":\"invalid email\"}");

        assertThatThrownBy(() -> this.gateway.update(
                        REALM, user("old"), user("new", new Credential(Credential.PASSWORD, null, null))))
                .isInstanceOf(KeycloakAccessException.class);
        verify(this.userApi, times(1)).updateUser(eq(REALM), eq("id"), any());
    }

    @Test
    void anUnchangedUserIsNotWritten() {
        this.gateway.update(REALM, user("same"), user("same"));

        verify(this.userApi, times(0)).updateUser(any(), any(), any());
    }
}
