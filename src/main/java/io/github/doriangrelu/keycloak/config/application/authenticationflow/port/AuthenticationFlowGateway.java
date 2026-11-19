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

package io.github.doriangrelu.keycloak.config.application.authenticationflow.port;

import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import java.util.List;
import java.util.Optional;

/** Authentication flows, authenticator configs and client flow bindings of a realm in Keycloak. */
public interface AuthenticationFlowGateway {

    /** Top-level flows with their tree, and built-in flows that are not top-level. */
    List<AuthenticationFlow> findAll(String realm);

    /** Creates a top-level flow with its tree and the authenticator configs of its executions. */
    void create(String realm, AuthenticationFlow flow);

    /**
     * Deletes and creates again a top-level flow whose tree changed. The realm, identity providers and clients using
     * it are moved to a temporary flow meanwhile, then bound to the new flow.
     */
    void recreate(String realm, AuthenticationFlow current, AuthenticationFlow target);

    /** Updates a built-in flow and the requirement of its declared executions. */
    void patchBuiltIn(String realm, AuthenticationFlow target);

    /** Deletes a top-level flow, unless an identity provider uses it. */
    void delete(String realm, String alias);

    List<AuthenticatorConfig> findAllAuthenticatorConfigs(String realm);

    /** Updates every configuration with the alias of {@code config}. */
    void updateAuthenticatorConfig(String realm, AuthenticatorConfig config);

    void deleteAuthenticatorConfig(String realm, String alias);

    /** Current bindings of a client, flows given by alias; empty when the client does not exist. */
    Optional<ClientFlowBindings> findClientFlowBindings(String realm, ClientFlowBindings client);

    void saveClientFlowBindings(String realm, ClientFlowBindings bindings);
}
