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
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientPolicy;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client policies, applied after the client profiles they refer to. As before, the declared policies are the exact
 * policies of the realm, whatever the remote state, and a declared policy replaces the current one. They are written
 * one at a time: Keycloak only writes whole lists.
 */
public final class ClientPolicyReconciler implements Reconciler<String, ClientPolicy> {

    private final ClientPolicyGateway clientPolicies;

    public ClientPolicyReconciler(final ClientPolicyGateway clientPolicies) {
        this.clientPolicies = Objects.requireNonNull(clientPolicies, "clientPolicies");
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT_POLICY;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.CLIENT_PROFILE);
    }

    @Override
    public List<ClientPolicy> read(final RealmContext context, final Collection<ClientPolicy> desired) {
        return this.clientPolicies.findAllPolicies(context.realm());
    }

    @Override
    public ClientPolicy merge(final RealmContext context, final ClientPolicy current, final ClientPolicy desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final ClientPolicy resource) {
        this.clientPolicies.savePolicy(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final ClientPolicy current, final ClientPolicy target) {
        this.clientPolicies.savePolicy(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final ClientPolicy resource) {
        this.clientPolicies.deletePolicy(context.realm(), resource.name());
    }

    @Override
    public String stateScope(final ClientPolicy resource) {
        return "client-policies";
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final ClientPolicy resource) {
        return false;
    }

    @Override
    public boolean usesRemoteState() {
        return false;
    }

    @Override
    public boolean appliesInParallel() {
        return false;
    }
}
