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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client;

import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Set;
import org.keycloak.representations.idm.ClientRepresentation;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/** Conversions between {@link ClientRepresentation} and {@link Client} (ADR-006). */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = ClientScopeKeycloakMapper.class)
public abstract class ClientKeycloakMapper {

    /**
     * Fields that are not unmodeled attributes: modeled by {@link Client}; computed by Keycloak ({@code id},
     * {@code access}); imported by the legacy services ({@code authorizationSettings},
     * {@code authenticationFlowBindingOverrides}); read-only and deprecated ({@code clientTemplate},
     * {@code useTemplate*}).
     */
    static final Set<String> NOT_UNMODELED = Set.of(
            "clientId",
            "name",
            "attributes",
            "defaultClientScopes",
            "optionalClientScopes",
            "protocolMappers",
            "secret",
            "bearerOnly",
            "publicClient",
            "serviceAccountsEnabled",
            "id",
            "access",
            "authorizationSettings",
            "authenticationFlowBindingOverrides",
            "clientTemplate",
            "useTemplateConfig",
            "useTemplateScope",
            "useTemplateMappers");

    private UnmodeledAttributesConverter unmodeledAttributes;

    @Autowired
    void setUnmodeledAttributes(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    public Client toClient(final ClientRepresentation representation) {
        return toClient(representation, this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    @Mapping(target = "unmodeled", source = "unmodeled")
    // Business methods, that MapStruct takes for fluent setters
    @Mapping(target = "mergedWith", ignore = true)
    @Mapping(target = "withClientId", ignore = true)
    @Mapping(target = "withoutValuesOf", ignore = true)
    protected abstract Client toClient(ClientRepresentation representation, UnmodeledAttributes unmodeled);

    /**
     * The other fields of the representation are written from the unmodeled attributes, after mapping: the strict
     * policy is relaxed for this direction only. Reading stays strict, so that every field of {@link Client} is mapped.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public abstract ClientRepresentation toRepresentation(Client client);

    @AfterMapping
    protected void applyUnmodeledAttributes(final Client client, @MappingTarget final ClientRepresentation target) {
        this.unmodeledAttributes.apply(client.unmodeled(), target);
    }
}
