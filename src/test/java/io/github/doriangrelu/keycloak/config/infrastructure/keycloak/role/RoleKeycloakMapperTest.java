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

import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;

class RoleKeycloakMapperTest {

    private RoleKeycloakMapper mapper;

    @BeforeEach
    void setUp() {
        RoleKeycloakMapperImpl impl = new RoleKeycloakMapperImpl();
        impl.setUnmodeledAttributes(new UnmodeledAttributesConverter());
        this.mapper = impl;
    }

    private static RoleRepresentation representation() {
        RoleRepresentation representation = new RoleRepresentation("admin", "Administrators", false);
        representation.setId("4f6c-uuid");
        representation.setContainerId("realm-uuid");
        representation.setAttributes(Map.of("level", List.of("high")));
        return representation;
    }

    @Test
    void readsARealmRole() {
        Role role = this.mapper.toRole(representation(), null);

        assertThat(role.key()).isEqualTo(RoleKey.realm("admin"));
        assertThat(role.description()).isEqualTo("Administrators");
        assertThat(role.attributes()).containsExactlyEntriesOf(Map.of("level", List.of("high")));
        assertThat(role.unmodeled().values()).as("ids are not business data").isEmpty();
    }

    @Test
    void readsAClientRole() {
        assertThat(this.mapper.toRole(representation(), "my-app").key()).isEqualTo(RoleKey.client("my-app", "admin"));
    }

    @Test
    void writesWhatItReads() {
        Role role = this.mapper.toRole(representation(), "my-app");

        RoleRepresentation written = this.mapper.toRepresentation(role);

        assertThat(written.getName()).isEqualTo("admin");
        assertThat(written.getDescription()).isEqualTo("Administrators");
        assertThat(written.getAttributes()).containsExactlyEntriesOf(Map.of("level", List.of("high")));
        assertThat(written.getClientRole()).isTrue();
        assertThat(written.getId()).isNull();
        assertThat(written.getComposites()).isNull();
        assertThat(this.mapper.toRole(written, "my-app")).isEqualTo(role);
    }

    @Test
    void leavesUnspecifiedFieldsEmpty() {
        RoleRepresentation fromFile = new RoleRepresentation();
        fromFile.setName("reader");

        Role role = this.mapper.toRole(fromFile, null);

        assertThat(role.description()).isNull();
        assertThat(role.attributes()).isNull();
    }
}
