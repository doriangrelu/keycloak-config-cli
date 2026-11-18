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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope;

import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ProtocolMapper;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Set;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/** Conversions between client scope representations and {@link ClientScope} (ADR-006). */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class ClientScopeKeycloakMapper {

    /** Modeled, computed by Keycloak ({@code id}) or read-only and deprecated ({@code consent*}). */
    static final Set<String> SCOPE_NOT_UNMODELED =
            Set.of("id", "name", "description", "protocol", "attributes", "protocolMappers");

    static final Set<String> MAPPER_NOT_UNMODELED =
            Set.of("id", "name", "protocol", "protocolMapper", "config", "consentRequired", "consentText");

    private UnmodeledAttributesConverter unmodeledAttributes;

    @Autowired
    void setUnmodeledAttributes(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /**
     * @param realmDefault whether the scope is a default or optional scope of the realm; {@code false} for a scope
     *     read from an import file
     */
    public ClientScope toClientScope(final ClientScopeRepresentation representation, final boolean realmDefault) {
        return toClientScope(
                representation, realmDefault, this.unmodeledAttributes.extract(representation, SCOPE_NOT_UNMODELED));
    }

    public ProtocolMapper toProtocolMapper(final ProtocolMapperRepresentation representation) {
        return toProtocolMapper(representation, this.unmodeledAttributes.extract(representation, MAPPER_NOT_UNMODELED));
    }

    @Mapping(target = "realmDefault", source = "realmDefault")
    @Mapping(target = "unmodeled", source = "unmodeled")
    // Business method, that MapStruct takes for a fluent setter
    @Mapping(target = "mergedWith", ignore = true)
    protected abstract ClientScope toClientScope(
            ClientScopeRepresentation representation, boolean realmDefault, UnmodeledAttributes unmodeled);

    @Mapping(target = "unmodeled", source = "unmodeled")
    @Mapping(target = "mergedWith", ignore = true)
    protected abstract ProtocolMapper toProtocolMapper(
            ProtocolMapperRepresentation representation, UnmodeledAttributes unmodeled);

    @Mapping(target = "id", ignore = true)
    public abstract ClientScopeRepresentation toRepresentation(ClientScope clientScope);

    @Mapping(target = "id", ignore = true)
    public abstract ProtocolMapperRepresentation toRepresentation(ProtocolMapper protocolMapper);

    @AfterMapping
    protected void applyUnmodeledAttributes(
            final ClientScope clientScope, @MappingTarget final ClientScopeRepresentation target) {
        this.unmodeledAttributes.apply(clientScope.unmodeled(), target);
    }

    @AfterMapping
    protected void applyUnmodeledAttributes(
            final ProtocolMapper protocolMapper, @MappingTarget final ProtocolMapperRepresentation target) {
        this.unmodeledAttributes.apply(protocolMapper.unmodeled(), target);
    }
}
