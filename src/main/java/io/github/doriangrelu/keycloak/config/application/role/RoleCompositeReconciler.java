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

package io.github.doriangrelu.keycloak.config.application.role;

import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.application.role.port.RoleGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Composites of the realm and client roles. Applied once every role exists, since a composite may include a role
 * declared after it. Only the composites of the roles that declare some are read and changed.
 */
public final class RoleCompositeReconciler implements Reconciler<RoleKey, RoleComposites> {

    private final RoleGateway roles;

    public RoleCompositeReconciler(final RoleGateway roles) {
        this.roles = Objects.requireNonNull(roles, "roles");
    }

    @Override
    public ResourceType type() {
        return ResourceType.ROLE_COMPOSITE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.REALM_ROLE, ResourceType.CLIENT_ROLE);
    }

    @Override
    public List<RoleComposites> read(final RealmContext context, final Collection<RoleComposites> desired) {
        return desired.stream()
                .map(composites -> this.roles.findComposites(context.realm(), composites.key()))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public RoleComposites merge(
            final RealmContext context, final RoleComposites current, final RoleComposites desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final RoleComposites resource) {
        update(context, RoleComposites.none(resource.key()), resource);
    }

    @Override
    public void update(final RealmContext context, final RoleComposites current, final RoleComposites target) {
        Set<RoleKey> toRemove = new LinkedHashSet<>(current.roles());
        toRemove.removeAll(target.roles());
        Set<RoleKey> toAdd = new LinkedHashSet<>(target.roles());
        toAdd.removeAll(current.roles());

        if (!toRemove.isEmpty()) {
            this.roles.removeComposites(context.realm(), target.key(), toRemove);
        }
        if (!toAdd.isEmpty()) {
            this.roles.addComposites(context.realm(), target.key(), toAdd);
        }
    }

    @Override
    public void delete(final RealmContext context, final RoleComposites resource) {
        throw new UnsupportedOperationException("Composites are removed with their role");
    }

    @Override
    public String stateScope(final RoleComposites resource) {
        return "roles-composites";
    }

    @Override
    public boolean isOwnedByKeycloak(final RoleComposites resource) {
        return false;
    }

    @Override
    public boolean prunes() {
        return false;
    }
}
