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
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProviderMapper;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mappers of the identity providers, managed by {@code import.managed.identity-provider-mapper} ({@code full} by
 * default). As before, deletions do not depend on the remote state, and cover the mappers of every identity provider
 * of the realm.
 */
public final class IdentityProviderMapperReconciler
        implements Reconciler<IdentityProviderMapper.Key, IdentityProviderMapper> {

    private final IdentityProviderGateway identityProviders;

    public IdentityProviderMapperReconciler(final IdentityProviderGateway identityProviders) {
        this.identityProviders = Objects.requireNonNull(identityProviders, "identityProviders");
    }

    @Override
    public ResourceType type() {
        return ResourceType.IDENTITY_PROVIDER_MAPPER;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.IDENTITY_PROVIDER);
    }

    @Override
    public List<IdentityProviderMapper> read(
            final RealmContext context, final Collection<IdentityProviderMapper> desired) {
        return this.identityProviders.findAllMappers(context.realm());
    }

    @Override
    public IdentityProviderMapper merge(
            final RealmContext context, final IdentityProviderMapper current, final IdentityProviderMapper desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final IdentityProviderMapper resource) {
        this.identityProviders.createMapper(context.realm(), resource);
    }

    @Override
    public void update(
            final RealmContext context, final IdentityProviderMapper current, final IdentityProviderMapper target) {
        this.identityProviders.updateMapper(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final IdentityProviderMapper resource) {
        this.identityProviders.deleteMapper(context.realm(), resource.key());
    }

    @Override
    public String stateScope(final IdentityProviderMapper resource) {
        return "identity-provider-mappers";
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final IdentityProviderMapper resource) {
        return false;
    }

    @Override
    public boolean usesRemoteState() {
        return false;
    }
}
