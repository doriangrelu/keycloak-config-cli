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

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/** Conversions between {@link RoleRepresentation} and {@link Role} (ADR-006). */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class RoleKeycloakMapper {

    /**
     * Fields that are not unmodeled attributes: modeled by {@link Role}, computed by Keycloak, imported apart
     * (composites), or read-only ({@code scopeParamRequired}, deprecated and without setter).
     */
    static final Set<String> NOT_UNMODELED = Set.of(
            "id",
            "name",
            "description",
            "attributes",
            "composite",
            "composites",
            "clientRole",
            "containerId",
            "scopeParamRequired");

    private UnmodeledAttributesConverter unmodeledAttributes;

    @Autowired
    void setUnmodeledAttributes(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /**
     * @param clientId client of a client role, {@code null} for a realm role
     */
    public Role toRole(final RoleRepresentation representation, final String clientId) {
        RoleKey key = clientId == null
                ? RoleKey.realm(representation.getName())
                : RoleKey.client(clientId, representation.getName());
        return toRole(representation, key, this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    /**
     * Composites declared by a role of an import file, or empty when the role declares none. A part of the composites
     * missing from the file is left unspecified.
     *
     * @param clientId client of a client role, {@code null} for a realm role
     */
    public Optional<RoleComposites> toComposites(final RoleRepresentation representation, final String clientId) {
        RoleRepresentation.Composites composites = representation.getComposites();
        if (composites == null || composites.getRealm() == null && composites.getClient() == null) {
            return Optional.empty();
        }
        RoleKey key = clientId == null
                ? RoleKey.realm(representation.getName())
                : RoleKey.client(clientId, representation.getName());
        Map<String, Set<String>> client = null;
        if (composites.getClient() != null) {
            client = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : composites.getClient().entrySet()) {
                client.put(entry.getKey(), entry.getValue() == null ? Set.of() : new LinkedHashSet<>(entry.getValue()));
            }
        }
        return Optional.of(new RoleComposites(key, composites.getRealm(), client));
    }

    @Mapping(target = "key", source = "key")
    @Mapping(target = "unmodeled", source = "unmodeled")
    @Mapping(target = "description", source = "representation.description")
    @Mapping(target = "attributes", source = "representation.attributes")
    // A business method, that MapStruct takes for a fluent setter
    @Mapping(target = "mergedWith", ignore = true)
    protected abstract Role toRole(RoleRepresentation representation, RoleKey key, UnmodeledAttributes unmodeled);

    @Mapping(target = "name", source = "key.name")
    @Mapping(target = "clientRole", expression = "java(role.key().isClientRole())")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "containerId", ignore = true)
    @Mapping(target = "composite", ignore = true)
    @Mapping(target = "composites", ignore = true)
    public abstract RoleRepresentation toRepresentation(Role role);

    @AfterMapping
    protected void applyUnmodeledAttributes(final Role role, @MappingTarget final RoleRepresentation representation) {
        this.unmodeledAttributes.apply(role.unmodeled(), representation);
    }
}
