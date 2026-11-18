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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider;

import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProvider;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProviderMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Set;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.springframework.stereotype.Component;

/** Conversions between the Keycloak representations of identity providers and their business objects (ADR-006). */
@Component
public class IdentityProviderKeycloakMapper {

    /** Fields that are not unmodeled attributes: modeled; computed by Keycloak ({@code internalId}). */
    static final Set<String> NOT_UNMODELED =
            Set.of("alias", "firstBrokerLoginFlowAlias", "postBrokerLoginFlowAlias", "internalId");

    /** Fields of a mapper that are not unmodeled attributes: its key; computed by Keycloak ({@code id}). */
    static final Set<String> MAPPER_NOT_UNMODELED = Set.of("identityProviderAlias", "name", "id");

    private final UnmodeledAttributesConverter unmodeledAttributes;

    public IdentityProviderKeycloakMapper(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    public IdentityProvider toIdentityProvider(final IdentityProviderRepresentation representation) {
        return new IdentityProvider(
                representation.getAlias(),
                representation.getFirstBrokerLoginFlowAlias(),
                representation.getPostBrokerLoginFlowAlias(),
                this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    public IdentityProviderRepresentation toRepresentation(final IdentityProvider identityProvider) {
        IdentityProviderRepresentation representation = new IdentityProviderRepresentation();
        this.unmodeledAttributes.apply(identityProvider.unmodeled(), representation);
        representation.setAlias(identityProvider.alias());
        representation.setFirstBrokerLoginFlowAlias(identityProvider.firstBrokerLoginFlowAlias());
        representation.setPostBrokerLoginFlowAlias(identityProvider.postBrokerLoginFlowAlias());
        return representation;
    }

    public IdentityProviderMapper toMapper(final IdentityProviderMapperRepresentation representation) {
        return new IdentityProviderMapper(
                new IdentityProviderMapper.Key(representation.getIdentityProviderAlias(), representation.getName()),
                this.unmodeledAttributes.extract(representation, MAPPER_NOT_UNMODELED));
    }

    public IdentityProviderMapperRepresentation toRepresentation(final IdentityProviderMapper mapper) {
        IdentityProviderMapperRepresentation representation = new IdentityProviderMapperRepresentation();
        this.unmodeledAttributes.apply(mapper.unmodeled(), representation);
        representation.setIdentityProviderAlias(mapper.key().identityProviderAlias());
        representation.setName(mapper.key().name());
        return representation;
    }
}
