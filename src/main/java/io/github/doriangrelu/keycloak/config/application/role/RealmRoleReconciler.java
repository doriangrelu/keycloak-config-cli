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
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Realm roles, managed by {@code import.managed.role}. */
public final class RealmRoleReconciler implements Reconciler<RoleKey, Role> {

    /** Remote state entry of the realm roles created by keycloak-config-cli. */
    public static final String STATE_SCOPE = "roles-realm";

    private final RoleGateway roles;

    public RealmRoleReconciler(final RoleGateway roles) {
        this.roles = Objects.requireNonNull(roles, "roles");
    }

    @Override
    public ResourceType type() {
        return ResourceType.REALM_ROLE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<Role> read(final RealmContext context, final Collection<Role> desired) {
        return this.roles.findRealmRoles(context.realm());
    }

    @Override
    public Role merge(final RealmContext context, final Role current, final Role desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final Role resource) {
        this.roles.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final Role current, final Role target) {
        this.roles.update(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final Role resource) {
        this.roles.delete(context.realm(), resource.key());
    }

    @Override
    public String stateScope(final Role resource) {
        return STATE_SCOPE;
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final Role resource) {
        return resource.isOwnedByKeycloak();
    }
}
