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

package io.github.doriangrelu.keycloak.config.application.component;

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Components of the realm ({@link ResourceType#COMPONENT}) or their sub-components
 * ({@link ResourceType#SUB_COMPONENT}).
 *
 * <p>The sub-components of every declared component are managed, even when the component declares none: in
 * {@code full} mode, the sub-components it does not declare are deleted, such as the default mappers Keycloak adds to
 * an LDAP user storage.
 */
public final class ComponentReconciler implements Reconciler<Component.Key, Component> {

    private final ResourceType type;
    private final ComponentGateway components;

    private ComponentReconciler(final ResourceType type, final ComponentGateway components) {
        this.type = type;
        this.components = Objects.requireNonNull(components, "components");
    }

    public static ComponentReconciler ofComponents(final ComponentGateway components) {
        return new ComponentReconciler(ResourceType.COMPONENT, components);
    }

    public static ComponentReconciler ofSubComponents(final ComponentGateway components) {
        return new ComponentReconciler(ResourceType.SUB_COMPONENT, components);
    }

    @Override
    public ResourceType type() {
        return this.type;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return this.type == ResourceType.COMPONENT ? Set.of() : Set.of(ResourceType.COMPONENT);
    }

    /** Components of the realm, or sub-components of the declared components. */
    @Override
    public List<Component> read(final RealmContext context, final Collection<Component> desired) {
        if (this.type == ResourceType.COMPONENT) {
            return this.components.findAll(context.realm()).stream()
                    .filter(component -> component.key().isTopLevel())
                    .toList();
        }
        Set<String> parents = context.scopes(this.type);
        return this.components.findAll(context.realm()).stream()
                .filter(component -> !component.key().isTopLevel()
                        && parents.contains(component.key().parent().path()))
                .toList();
    }

    @Override
    public Component merge(final RealmContext context, final Component current, final Component desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final Component resource) {
        this.components.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final Component current, final Component target) {
        this.components.update(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final Component resource) {
        this.components.delete(context.realm(), resource.key());
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final Component resource) {
        return resource.isOwnedByKeycloak();
    }
}
