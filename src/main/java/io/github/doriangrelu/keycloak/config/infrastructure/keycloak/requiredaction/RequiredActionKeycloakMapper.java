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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Set;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/** Conversions between {@link RequiredActionProviderRepresentation} and {@link RequiredAction} (ADR-006). */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class RequiredActionKeycloakMapper {

    static final Set<String> MODELED =
            Set.of("alias", "name", "providerId", "enabled", "defaultAction", "priority", "config");

    private UnmodeledAttributesConverter unmodeledAttributes;

    @Autowired
    void setUnmodeledAttributes(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    public RequiredAction toRequiredAction(final RequiredActionProviderRepresentation representation) {
        return toRequiredAction(representation, this.unmodeledAttributes.extract(representation, MODELED));
    }

    @Mapping(target = "unmodeled", source = "unmodeled")
    // Business methods, that MapStruct takes for fluent setters
    @Mapping(target = "mergedWith", ignore = true)
    protected abstract RequiredAction toRequiredAction(
            RequiredActionProviderRepresentation representation, UnmodeledAttributes unmodeled);

    public abstract RequiredActionProviderRepresentation toRepresentation(RequiredAction requiredAction);

    @AfterMapping
    protected void applyUnmodeledAttributes(
            final RequiredAction requiredAction, @MappingTarget final RequiredActionProviderRepresentation target) {
        this.unmodeledAttributes.apply(requiredAction.unmodeled(), target);
    }
}
