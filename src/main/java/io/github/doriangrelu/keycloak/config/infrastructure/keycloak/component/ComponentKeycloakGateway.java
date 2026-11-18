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

import io.github.doriangrelu.keycloak.config.application.component.ComponentRejectedException;
import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ComponentApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** {@link ComponentGateway} on the Keycloak Admin API. Components are addressed by their key, resolved to an id. */
@org.springframework.stereotype.Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ComponentKeycloakGateway implements ComponentGateway {

    private static final Logger logger = LoggerFactory.getLogger(ComponentKeycloakGateway.class);

    static final String USER_STORAGE_PROVIDER = "org.keycloak.storage.UserStorageProvider";

    private static final Set<KeycloakFailure> REFUSALS = Set.of(
            KeycloakFailure.REJECTED, KeycloakFailure.NOT_FOUND, KeycloakFailure.CONFLICT, KeycloakFailure.UNEXPECTED);

    private final ComponentApi componentApi;
    private final ComponentKeycloakMapper mapper;

    public ComponentKeycloakGateway(final ComponentApi componentApi, final ComponentKeycloakMapper mapper) {
        this.componentApi = componentApi;
        this.mapper = mapper;
    }

    /** Components of a realm by key, and the id of the realm, parent of its top-level components. */
    private record Tree(String realmId, Map<Component.Key, ComponentRepresentation> byKey) {}

    private Tree tree(final String realm) {
        String realmId = this.componentApi.realmId(realm);
        List<ComponentRepresentation> all = this.componentApi.listComponents(realm);
        Map<String, ComponentRepresentation> byId = new HashMap<>();
        all.forEach(component -> byId.put(component.getId(), component));
        Map<String, Component.Key> keys = new HashMap<>();
        Map<Component.Key, ComponentRepresentation> byKey = new LinkedHashMap<>();
        for (ComponentRepresentation component : all) {
            keyOf(component, realmId, byId, keys, Set.of()).ifPresent(key -> byKey.put(key, component));
        }
        return new Tree(realmId, byKey);
    }

    /** Key of a component, empty when one of its ancestors is missing. */
    private static Optional<Component.Key> keyOf(
            final ComponentRepresentation component,
            final String realmId,
            final Map<String, ComponentRepresentation> byId,
            final Map<String, Component.Key> keys,
            final Set<String> visiting) {
        Component.Key known = keys.get(component.getId());
        if (known != null) {
            return Optional.of(known);
        }
        Component.Key parent = null;
        String parentId = component.getParentId();
        if (parentId != null && !Objects.equals(parentId, realmId)) {
            ComponentRepresentation parentComponent = byId.get(parentId);
            if (parentComponent == null || visiting.contains(parentId)) {
                return Optional.empty();
            }
            Set<String> path = new java.util.HashSet<>(visiting);
            path.add(component.getId());
            Optional<Component.Key> parentKey = keyOf(parentComponent, realmId, byId, keys, path);
            if (parentKey.isEmpty()) {
                return Optional.empty();
            }
            parent = parentKey.get();
        }
        Component.Key key =
                new Component.Key(parent, component.getProviderType(), component.getSubType(), component.getName());
        keys.put(component.getId(), key);
        return Optional.of(key);
    }

    @Override
    public List<Component> findAll(final String realm) {
        List<Component> components = new ArrayList<>();
        tree(realm)
                .byKey()
                .forEach((key, component) -> components.add(this.mapper.toComponent(component, key.parent())));
        return components;
    }

    @Override
    public void create(final String realm, final Component component) {
        Tree tree = tree(realm);
        String parentId = component.key().isTopLevel()
                ? tree.realmId()
                : Optional.ofNullable(tree.byKey().get(component.key().parent()))
                        .map(ComponentRepresentation::getId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Parent of component " + component.key() + " is missing in realm " + realm));
        try {
            this.componentApi.createComponent(realm, this.mapper.toRepresentation(component, parentId));
        } catch (final KeycloakAccessException error) {
            throw REFUSALS.contains(error.getFailure())
                    ? ComponentRejectedException.onCreate(component.key().name(), realm, error)
                    : error;
        }
    }

    @Override
    public void update(final String realm, final Component component) {
        ComponentRepresentation existing = tree(realm).byKey().get(component.key());
        if (existing == null) {
            throw new IllegalStateException("Component " + component.key() + " is missing in realm " + realm);
        }
        this.componentApi.updateComponent(
                realm,
                this.mapper.toRepresentation(
                        new io.github.doriangrelu.keycloak.config.domain.component.model.Component(
                                component.key(), existing.getId(), component.providerId(), component.config()),
                        existing.getParentId()));
    }

    @Override
    public void delete(final String realm, final Component.Key key) {
        ComponentRepresentation existing = tree(realm).byKey().get(key);
        if (existing != null) {
            this.componentApi.deleteComponent(realm, existing.getId());
        }
    }

    @Override
    public void syncImportingUserStorages(final String realm) {
        this.componentApi.listComponents(realm).stream()
                .filter(component -> USER_STORAGE_PROVIDER.equals(component.getProviderType()))
                .filter(component -> component.getConfig() != null
                        && component.getConfig().getOrDefault("importEnabled", List.of("false")).stream()
                                .allMatch(Boolean::parseBoolean))
                .forEach(component -> {
                    logger.debug("Syncing user from federation '{}' for realm '{}'...", component.getName(), realm);
                    this.componentApi.syncUsers(realm, component.getId());
                });
    }
}
