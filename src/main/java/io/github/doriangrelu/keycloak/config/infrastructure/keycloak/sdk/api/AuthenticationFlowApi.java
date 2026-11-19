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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api;

import java.util.List;
import java.util.Map;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

/** Authentication flow operations of the Keycloak Admin API (ADR-003). */
public interface AuthenticationFlowApi {

    /**
     * Every flow of the realm, top-level or not, with its executions. The flow list of the API misses some top-level
     * flows: this comes from a partial export.
     */
    List<AuthenticationFlowRepresentation> listFlows(String realm);

    /** Every authenticator config of the realm, from a partial export. */
    List<AuthenticatorConfigRepresentation> listAuthenticatorConfigs(String realm);

    /** @return the id of the created flow */
    String createTopLevelFlow(String realm, AuthenticationFlowRepresentation flow);

    void updateFlow(String realm, String id, AuthenticationFlowRepresentation flow);

    void deleteFlow(String realm, String id);

    /** Executions of a flow and of its sub-flows, in order. */
    List<AuthenticationExecutionInfoRepresentation> listExecutions(String realm, String flowAlias);

    /** @return the id of the created execution */
    String addExecution(String realm, AuthenticationExecutionRepresentation execution);

    void addExecutionToFlow(String realm, String flowAlias, Map<String, Object> execution);

    void addSubFlow(String realm, String flowAlias, Map<String, Object> subFlow);

    void updateExecution(String realm, String flowAlias, AuthenticationExecutionInfoRepresentation execution);

    void createExecutionConfig(String realm, String executionId, AuthenticatorConfigRepresentation config);

    void updateAuthenticatorConfig(String realm, String id, AuthenticatorConfigRepresentation config);

    void deleteAuthenticatorConfig(String realm, String id);

    RealmRepresentation getRealm(String realm);

    void updateRealm(String realm, RealmRepresentation representation);
}
