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
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authenticator configs: the declared ones are updated once the flows are applied, and the clean phase deletes the
 * configs no execution uses any more. A configuration an execution uses is never deleted.
 */
public final class AuthenticatorConfigReconciler implements Reconciler<String, AuthenticatorConfig> {

    private final AuthenticationFlowGateway flows;

    public AuthenticatorConfigReconciler(final AuthenticationFlowGateway flows) {
        this.flows = Objects.requireNonNull(flows, "flows");
    }

    @Override
    public ResourceType type() {
        return ResourceType.AUTHENTICATOR_CONFIG;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.AUTHENTICATION_FLOW);
    }

    @Override
    public List<AuthenticatorConfig> read(final RealmContext context, final Collection<AuthenticatorConfig> desired) {
        return this.flows.findAllAuthenticatorConfigs(context.realm());
    }

    @Override
    public AuthenticatorConfig merge(
            final RealmContext context, final AuthenticatorConfig current, final AuthenticatorConfig desired) {
        return current.mergedWith(desired);
    }

    /** Only an execution creates a configuration. */
    @Override
    public void create(final RealmContext context, final AuthenticatorConfig resource) {
        throw UnknownAuthenticatorConfigException.notUsed(resource.alias());
    }

    @Override
    public void update(
            final RealmContext context, final AuthenticatorConfig current, final AuthenticatorConfig target) {
        this.flows.updateAuthenticatorConfig(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final AuthenticatorConfig resource) {
        this.flows.deleteAuthenticatorConfig(context.realm(), resource.alias());
    }

    @Override
    public String stateScope(final AuthenticatorConfig resource) {
        return "authenticator-configs";
    }

    /** A configuration an execution uses goes with its execution. */
    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final AuthenticatorConfig resource) {
        return resource.inUse();
    }

    @Override
    public boolean usesRemoteState() {
        return false;
    }
}
