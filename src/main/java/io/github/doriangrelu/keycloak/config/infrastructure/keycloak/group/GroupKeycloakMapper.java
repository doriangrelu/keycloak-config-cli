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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.representations.idm.GroupRepresentation;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/** Conversions between {@link GroupRepresentation} and {@link Group} (ADR-006). */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class GroupKeycloakMapper {

    /** Modeled by {@link Group}, computed by Keycloak, or imported apart (subgroups, role mappings). */
    static final Set<String> NOT_UNMODELED = Set.of(
            "id",
            "name",
            "path",
            "parentId",
            "subGroupCount",
            "subGroups",
            "attributes",
            "realmRoles",
            "clientRoles",
            "access");

    private UnmodeledAttributesConverter unmodeledAttributes;

    @Autowired
    void setUnmodeledAttributes(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /**
     * @param path path of the group, computed from its parents for a group of an import file
     * @param subGroups names of its children, or {@code null} when not specified
     */
    public Group toGroup(final GroupRepresentation representation, final String path, final Set<String> subGroups) {
        return toGroup(
                representation, path, subGroups, this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    @Mapping(target = "path", source = "path")
    @Mapping(target = "subGroups", source = "subGroups")
    @Mapping(target = "unmodeled", source = "unmodeled")
    // Business method, that MapStruct takes for a fluent setter
    @Mapping(target = "mergedWith", ignore = true)
    protected abstract Group toGroup(
            GroupRepresentation representation, String path, Set<String> subGroups, UnmodeledAttributes unmodeled);

    /**
     * Only the group itself: role mappings and children have their own endpoints. The other fields of the
     * representation are written from the unmodeled attributes, after mapping.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "name", expression = "java(group.name())")
    @Mapping(target = "attributes", source = "attributes")
    @Mapping(target = "path", ignore = true)
    @Mapping(target = "subGroups", ignore = true)
    @Mapping(target = "realmRoles", ignore = true)
    @Mapping(target = "clientRoles", ignore = true)
    public abstract GroupRepresentation toRepresentation(Group group);

    @AfterMapping
    protected void applyUnmodeledAttributes(final Group group, @MappingTarget final GroupRepresentation target) {
        this.unmodeledAttributes.apply(group.unmodeled(), target);
    }

    protected Map<String, Set<String>> toRoleSets(final Map<String, List<String>> roles) {
        if (roles == null) {
            return null;
        }
        Map<String, Set<String>> sets = new LinkedHashMap<>();
        roles.forEach((clientId, names) -> sets.put(clientId, names == null ? Set.of() : new LinkedHashSet<>(names)));
        return sets;
    }
}
