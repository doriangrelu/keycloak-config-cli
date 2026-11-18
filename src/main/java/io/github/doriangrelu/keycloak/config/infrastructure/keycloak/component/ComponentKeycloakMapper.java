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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.component;

import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;

/**
 * Conversions between the Keycloak representations of components and their business objects (ADR-006). A component
 * has no unmodeled field: its id and the id of its parent are computed by Keycloak.
 */
@org.springframework.stereotype.Component
public class ComponentKeycloakMapper {

    public Component toComponent(final ComponentRepresentation representation, final Component.Key parent) {
        return new Component(
                new Component.Key(
                        parent,
                        representation.getProviderType(),
                        representation.getSubType(),
                        representation.getName()),
                representation.getId(),
                representation.getProviderId(),
                configOf(representation.getConfig()));
    }

    /**
     * Components declared by an import file, sub-components included, each one after its parent.
     *
     * @param components declared components by provider type
     */
    public List<Component> flatten(final Map<String, List<ComponentExportRepresentation>> components) {
        List<Component> flattened = new ArrayList<>();
        flatten(components, null, flattened);
        return flattened;
    }

    private void flatten(
            final Map<String, List<ComponentExportRepresentation>> components,
            final Component.Key parent,
            final List<Component> flattened) {
        if (components == null) {
            return;
        }
        components.forEach((providerType, declared) -> {
            if (declared == null) {
                return;
            }
            for (ComponentExportRepresentation component : declared) {
                Component.Key key =
                        new Component.Key(parent, providerType, component.getSubType(), component.getName());
                flattened.add(new Component(
                        key, component.getId(), component.getProviderId(), configOf(component.getConfig())));
                flatten(component.getSubComponents(), key, flattened);
            }
        });
    }

    public ComponentRepresentation toRepresentation(final Component component, final String parentId) {
        ComponentRepresentation representation = new ComponentRepresentation();
        representation.setId(component.id());
        representation.setParentId(parentId);
        representation.setName(component.key().name());
        representation.setProviderType(component.key().providerType());
        representation.setSubType(component.key().subType());
        representation.setProviderId(component.providerId());
        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        component.config().forEach((name, values) -> config.put(name, new ArrayList<>(values)));
        representation.setConfig(config);
        return representation;
    }

    private static Map<String, List<String>> configOf(final Map<String, List<String>> config) {
        return config == null ? Map.of() : new LinkedHashMap<>(config);
    }
}
