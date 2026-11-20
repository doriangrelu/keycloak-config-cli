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

package io.github.doriangrelu.keycloak.config.application.clientscope;

import io.github.doriangrelu.keycloak.config.application.clientscope.port.ClientScopeGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client scopes, managed by {@code import.managed.client-scope} ({@code no-delete} by default). As before, deletions
 * do not depend on the remote state, and the default and optional scopes of the realm are never deleted.
 */
public final class ClientScopeReconciler implements Reconciler<String, ClientScope> {

    private final ClientScopeGateway clientScopes;

    public ClientScopeReconciler(final ClientScopeGateway clientScopes) {
        this.clientScopes = Objects.requireNonNull(clientScopes, "clientScopes");
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT_SCOPE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<ClientScope> read(final RealmContext context, final Collection<ClientScope> desired) {
        return this.clientScopes.findAll(context.realm());
    }

    @Override
    public ClientScope merge(final RealmContext context, final ClientScope current, final ClientScope desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final ClientScope resource) {
        this.clientScopes.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final ClientScope current, final ClientScope target) {
        this.clientScopes.update(context.realm(), current, target);
    }

    @Override
    public void delete(final RealmContext context, final ClientScope resource) {
        this.clientScopes.delete(context.realm(), resource.name());
    }

    @Override
    public String stateScope(final ClientScope resource) {
        return "client-scopes";
    }

    /** The default and optional client scopes of the realm stay, as in the legacy import. */
    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final ClientScope resource) {
        return resource.isOwnedByKeycloak();
    }

    @Override
    public boolean usesRemoteState() {
        return false;
    }
}
