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

package io.github.doriangrelu.keycloak.config.application.clientpolicy;

import io.github.doriangrelu.keycloak.config.application.clientpolicy.port.ClientPolicyGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientProfile;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client profiles. As before, the declared profiles are the exact profiles of the realm, whatever the remote state, and
 * a declared profile replaces the current one. They are written one at a time: Keycloak only writes whole lists.
 */
public final class ClientProfileReconciler implements Reconciler<String, ClientProfile> {

    private final ClientPolicyGateway clientPolicies;

    public ClientProfileReconciler(final ClientPolicyGateway clientPolicies) {
        this.clientPolicies = Objects.requireNonNull(clientPolicies, "clientPolicies");
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT_PROFILE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<ClientProfile> read(final RealmContext context, final Collection<ClientProfile> desired) {
        return this.clientPolicies.findAllProfiles(context.realm());
    }

    @Override
    public ClientProfile merge(final RealmContext context, final ClientProfile current, final ClientProfile desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final ClientProfile resource) {
        this.clientPolicies.saveProfile(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final ClientProfile current, final ClientProfile target) {
        this.clientPolicies.saveProfile(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final ClientProfile resource) {
        this.clientPolicies.deleteProfile(context.realm(), resource.name());
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final ClientProfile resource) {
        return false;
    }

    @Override
    public boolean appliesInParallel() {
        return false;
    }
}
