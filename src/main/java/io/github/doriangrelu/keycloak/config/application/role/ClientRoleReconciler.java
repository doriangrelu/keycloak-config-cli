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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Client roles, managed by {@code import.managed.role}. Only the clients for which an import file declares roles are
 * read and cleaned: the roles of the other clients are left untouched.
 */
public final class ClientRoleReconciler implements Reconciler<RoleKey, Role> {

    private final RoleGateway roles;

    public ClientRoleReconciler(final RoleGateway roles) {
        this.roles = Objects.requireNonNull(roles, "roles");
    }

    /** Remote state entry of the roles of a client created by keycloak-config-cli. */
    public static String stateScope(final String clientId) {
        return "roles-client-" + clientId;
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT_ROLE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.REALM_ROLE);
    }

    /**
     * Roles of the clients declared by the import files, including the clients declared with no role. A client missing
     * in Keycloak has no role: the creation of its roles reports the missing client.
     */
    @Override
    public List<Role> read(final RealmContext context, final Collection<Role> desired) {
        return Stream.concat(
                        context.scopes(ResourceType.CLIENT_ROLE).stream(),
                        desired.stream().map(role -> role.key().clientId()))
                .distinct()
                .map(clientId -> this.roles.findClientRoles(context.realm(), clientId))
                .flatMap(Optional::stream)
                .flatMap(List::stream)
                .toList();
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
        return stateScope(resource.key().clientId());
    }

    @Override
    public boolean isOwnedByKeycloak(final Role resource) {
        return resource.isOwnedByKeycloak();
    }
}
