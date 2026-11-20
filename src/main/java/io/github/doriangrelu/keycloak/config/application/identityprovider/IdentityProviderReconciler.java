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

package io.github.doriangrelu.keycloak.config.application.identityprovider;

import io.github.doriangrelu.keycloak.config.application.identityprovider.port.IdentityProviderGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProvider;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Identity providers, managed by {@code import.managed.identity-provider} ({@code full} by default). As before,
 * deletions do not depend on the remote state.
 */
public final class IdentityProviderReconciler implements Reconciler<String, IdentityProvider> {

    private final IdentityProviderGateway identityProviders;

    public IdentityProviderReconciler(final IdentityProviderGateway identityProviders) {
        this.identityProviders = Objects.requireNonNull(identityProviders, "identityProviders");
    }

    @Override
    public ResourceType type() {
        return ResourceType.IDENTITY_PROVIDER;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<IdentityProvider> read(final RealmContext context, final Collection<IdentityProvider> desired) {
        return this.identityProviders.findAll(context.realm());
    }

    @Override
    public IdentityProvider merge(
            final RealmContext context, final IdentityProvider current, final IdentityProvider desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final IdentityProvider resource) {
        this.identityProviders.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final IdentityProvider current, final IdentityProvider target) {
        this.identityProviders.update(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final IdentityProvider resource) {
        this.identityProviders.delete(context.realm(), resource.alias());
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final IdentityProvider resource) {
        return false;
    }
}
