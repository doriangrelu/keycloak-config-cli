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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.AuthenticationFlowApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

/** {@link AuthenticationFlowApi} on the Keycloak Admin client. */
public class AuthenticationFlowAdapter extends AbstractAdapter implements AuthenticationFlowApi {

    public AuthenticationFlowAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    private AuthenticationManagementResource flows(final String realm) {
        return keycloak().realm(realm).flows();
    }

    private RealmRepresentation export(final String realm) {
        return execute(() -> keycloak().realm(realm).partialExport(false, false));
    }

    @Override
    public List<AuthenticationFlowRepresentation> listFlows(final String realm) {
        List<AuthenticationFlowRepresentation> flows = export(realm).getAuthenticationFlows();
        return flows == null ? List.of() : flows;
    }

    @Override
    public List<AuthenticatorConfigRepresentation> listAuthenticatorConfigs(final String realm) {
        List<AuthenticatorConfigRepresentation> configs = export(realm).getAuthenticatorConfig();
        return configs == null ? List.of() : configs;
    }

    @Override
    public String createTopLevelFlow(final String realm, final AuthenticationFlowRepresentation flow) {
        return createAndGetId(() -> flows(realm).createFlow(flow));
    }

    @Override
    public void updateFlow(final String realm, final String id, final AuthenticationFlowRepresentation flow) {
        execute(() -> flows(realm).updateFlow(id, flow));
    }

    @Override
    public void deleteFlow(final String realm, final String id) {
        execute(() -> flows(realm).deleteFlow(id));
    }

    @Override
    public List<AuthenticationExecutionInfoRepresentation> listExecutions(final String realm, final String flowAlias) {
        List<AuthenticationExecutionInfoRepresentation> executions =
                execute(() -> flows(realm).getExecutions(flowAlias));
        return executions == null ? List.of() : executions;
    }

    @Override
    public String addExecution(final String realm, final AuthenticationExecutionRepresentation execution) {
        return createAndGetId(() -> flows(realm).addExecution(execution));
    }

    @Override
    public void addExecutionToFlow(final String realm, final String flowAlias, final Map<String, Object> execution) {
        execute(() -> flows(realm).addExecution(flowAlias, new HashMap<>(execution)));
    }

    @Override
    public void addSubFlow(final String realm, final String flowAlias, final Map<String, Object> subFlow) {
        execute(() -> flows(realm).addExecutionFlow(flowAlias, new HashMap<>(subFlow)));
    }

    @Override
    public void updateExecution(
            final String realm, final String flowAlias, final AuthenticationExecutionInfoRepresentation execution) {
        execute(() -> flows(realm).updateExecutions(flowAlias, execution));
    }

    @Override
    public void createExecutionConfig(
            final String realm, final String executionId, final AuthenticatorConfigRepresentation config) {
        createAndGetId(() -> flows(realm).newExecutionConfig(executionId, config));
    }

    @Override
    public void updateAuthenticatorConfig(
            final String realm, final String id, final AuthenticatorConfigRepresentation config) {
        execute(() -> flows(realm).updateAuthenticatorConfig(id, config));
    }

    @Override
    public void deleteAuthenticatorConfig(final String realm, final String id) {
        execute(() -> flows(realm).removeAuthenticatorConfig(id));
    }

    @Override
    public RealmRepresentation getRealm(final String realm) {
        return execute(() -> keycloak().realm(realm).toRepresentation());
    }

    @Override
    public void updateRealm(final String realm, final RealmRepresentation representation) {
        execute(() -> keycloak().realm(realm).update(representation));
    }
}
