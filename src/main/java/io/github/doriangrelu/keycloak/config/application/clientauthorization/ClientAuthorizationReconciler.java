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

package io.github.doriangrelu.keycloak.config.application.clientauthorization;

import io.github.doriangrelu.keycloak.config.application.clientauthorization.port.ClientAuthorizationGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Authorization services of the declared clients. The resources, policies and scopes the import misses are deleted
 * while the authorization is applied. Resources follow the remote
 * state; policies and scopes do not. The authorization of an undeclared client is left as it is.
 */
public final class ClientAuthorizationReconciler implements Reconciler<String, ClientAuthorization> {

    private final ClientAuthorizationGateway authorizations;

    public ClientAuthorizationReconciler(final ClientAuthorizationGateway authorizations) {
        this.authorizations = Objects.requireNonNull(authorizations, "authorizations");
    }

    /** Remote state entry of the authorization resources of a client. */
    public static String resourcesStateScope(final String clientKey) {
        return "resources-client-" + clientKey;
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT_AUTHORIZATION;
    }

    /** Policies and resources name clients, roles, groups and identity providers. */
    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(
                ResourceType.CLIENT,
                ResourceType.REALM_ROLE,
                ResourceType.CLIENT_ROLE,
                ResourceType.GROUP,
                ResourceType.IDENTITY_PROVIDER);
    }

    @Override
    public List<ClientAuthorization> read(final RealmContext context, final Collection<ClientAuthorization> desired) {
        return desired.stream()
                .map(client -> this.authorizations.find(context.realm(), client))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public ClientAuthorization merge(
            final RealmContext context, final ClientAuthorization current, final ClientAuthorization desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final ClientAuthorization resource) {
        apply(context, resource);
    }

    @Override
    public void update(
            final RealmContext context, final ClientAuthorization current, final ClientAuthorization target) {
        apply(context, target);
    }

    private void apply(final RealmContext context, final ClientAuthorization target) {
        this.authorizations.apply(
                context.realm(),
                target,
                new ClientAuthorizationGateway.Deletions(context.managedState(resourcesStateScope(target.key()))));
    }

    @Override
    public void delete(final RealmContext context, final ClientAuthorization resource) {
        throw new UnsupportedOperationException("The authorization of a client is never deleted on its own");
    }

    @Override
    public String stateScope(final ClientAuthorization resource) {
        return resourcesStateScope(resource.key());
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final ClientAuthorization resource) {
        return false;
    }

    @Override
    public boolean prunes() {
        return false;
    }
}
