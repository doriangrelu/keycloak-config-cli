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

package io.github.doriangrelu.keycloak.config.application.authenticationflow;

import io.github.doriangrelu.keycloak.config.application.authenticationflow.port.AuthenticationFlowGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Flow bindings of the declared clients, applied once their flows exist. They live in the client: they are never
 * deleted on their own.
 */
public final class ClientFlowBindingsReconciler implements Reconciler<String, ClientFlowBindings> {

    private final AuthenticationFlowGateway flows;

    public ClientFlowBindingsReconciler(final AuthenticationFlowGateway flows) {
        this.flows = Objects.requireNonNull(flows, "flows");
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT_FLOW_BINDING;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.CLIENT, ResourceType.AUTHENTICATION_FLOW);
    }

    @Override
    public List<ClientFlowBindings> read(final RealmContext context, final Collection<ClientFlowBindings> desired) {
        return desired.stream()
                .map(client -> this.flows.findClientFlowBindings(context.realm(), client))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public ClientFlowBindings merge(
            final RealmContext context, final ClientFlowBindings current, final ClientFlowBindings desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final ClientFlowBindings resource) {
        this.flows.saveClientFlowBindings(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final ClientFlowBindings current, final ClientFlowBindings target) {
        this.flows.saveClientFlowBindings(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final ClientFlowBindings resource) {
        throw new UnsupportedOperationException("Client flow bindings are never deleted on their own");
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final ClientFlowBindings resource) {
        return false;
    }

    @Override
    public boolean prunes() {
        return false;
    }
}
