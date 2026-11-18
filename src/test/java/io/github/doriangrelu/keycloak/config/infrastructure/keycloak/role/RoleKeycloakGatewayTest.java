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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.application.role.UnknownClientException;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mockito.InOrder;

class RoleKeycloakGatewayTest {

    private final RoleApi api = mock(RoleApi.class);
    private RoleKeycloakGateway gateway;

    @BeforeEach
    void setUp() {
        RoleKeycloakMapperImpl mapper = new RoleKeycloakMapperImpl();
        mapper.setUnmodeledAttributes(new UnmodeledAttributesConverter());
        this.gateway = new RoleKeycloakGateway(this.api, mapper);
    }

    private static RoleRepresentation representation(String name, Boolean clientRole) {
        RoleRepresentation representation = new RoleRepresentation();
        representation.setName(name);
        representation.setClientRole(clientRole);
        return representation;
    }

    @Test
    void realmRolesExcludeClientRoles() {
        when(this.api.listRealmRoles("test"))
                .thenReturn(List.of(representation("admin", false), representation("app-role", true)));

        assertThat(this.gateway.findRealmRoles("test")).extracting(Role::key).containsExactly(RoleKey.realm("admin"));
    }

    @Test
    void rolesOfAMissingClientAreEmpty() {
        when(this.api.findClientUuid("test", "missing")).thenReturn(Optional.empty());

        assertThat(this.gateway.findClientRoles("test", "missing")).isEmpty();
    }

    @Test
    void clientRolesAreCreatedThenUpdatedToKeepTheirAttributes() {
        when(this.api.findClientUuid("test", "app")).thenReturn(Optional.of("app-uuid"));

        this.gateway.create("test", new Role(RoleKey.client("app", "reader"), null, null, null));

        InOrder order = inOrder(this.api);
        order.verify(this.api).createClientRole(anyString(), anyString(), any());
        order.verify(this.api).updateClientRole(anyString(), anyString(), anyString(), any());
    }

    @Test
    void creatingARoleForAMissingClientIsReported() {
        when(this.api.findClientUuid("test", "missing")).thenReturn(Optional.empty());
        Role role = new Role(RoleKey.client("missing", "reader"), null, null, null);

        assertThatThrownBy(() -> this.gateway.create("test", role)).isInstanceOf(UnknownClientException.class);
    }

    @Test
    void deletingARoleOfADeletedClientDoesNothing() {
        when(this.api.findClientUuid("test", "gone")).thenReturn(Optional.empty());

        this.gateway.delete("test", RoleKey.client("gone", "reader"));

        verify(this.api, never()).deleteClientRole(anyString(), anyString(), anyString());
    }

    @Test
    void realmRolesAreAddressedByName() {
        this.gateway.delete("test", RoleKey.realm("admin"));

        verify(this.api).deleteRealmRole("test", "admin");
    }
}
