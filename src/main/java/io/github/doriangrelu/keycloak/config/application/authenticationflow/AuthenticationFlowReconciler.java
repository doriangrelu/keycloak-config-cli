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
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.InvalidAuthenticationFlowException;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authentication flows, managed by {@code import.managed.authentication-flow} ({@code full} by default). A top-level
 * flow whose tree changes is recreated; built-in flows are only patched and never deleted. As before, deletions do not
 * depend on the remote state.
 */
public final class AuthenticationFlowReconciler implements Reconciler<String, AuthenticationFlow> {

    private final AuthenticationFlowGateway flows;

    public AuthenticationFlowReconciler(final AuthenticationFlowGateway flows) {
        this.flows = Objects.requireNonNull(flows, "flows");
    }

    @Override
    public ResourceType type() {
        return ResourceType.AUTHENTICATION_FLOW;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<AuthenticationFlow> read(final RealmContext context, final Collection<AuthenticationFlow> desired) {
        return this.flows.findAll(context.realm());
    }

    @Override
    public AuthenticationFlow merge(
            final RealmContext context, final AuthenticationFlow current, final AuthenticationFlow desired) {
        return current.mergedWith(desired, context.realm());
    }

    @Override
    public void create(final RealmContext context, final AuthenticationFlow resource) {
        if (resource.builtIn()) {
            throw new InvalidAuthenticationFlowException(String.format(
                    "Cannot create flow '%s' in realm '%s': Unable to create built-in flows.",
                    resource.alias(), context.realm()));
        }
        this.flows.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final AuthenticationFlow current, final AuthenticationFlow target) {
        if (target.builtIn()) {
            this.flows.patchBuiltIn(context.realm(), target);
        } else {
            this.flows.recreate(context.realm(), current, target);
        }
    }

    @Override
    public void delete(final RealmContext context, final AuthenticationFlow resource) {
        this.flows.delete(context.realm(), resource.alias());
    }

    /** Built-in flows belong to Keycloak. */
    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final AuthenticationFlow resource) {
        return resource.builtIn();
    }
}
