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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.authenticationflow;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.AuthenticationFlowApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak refuses to delete a flow the realm, an identity provider or a client uses. To recreate such a flow, its
 * users are moved to a temporary flow meanwhile, then bound to the recreated flow. One instance per recreated flow.
 */
final class UsedFlowWorkaround {

    private static final Logger logger = LoggerFactory.getLogger(UsedFlowWorkaround.class);

    static final String TEMPORARY_FLOW = "TEMPORARY_CREATED_AUTH_FLOW";
    static final String TEMPORARY_CLIENT_FLOW = "TEMPORARY_CREATED_CLIENT_AUTH_FLOW";

    /** A flow binding of the realm. */
    private record RealmBinding(
            String name,
            Function<RealmRepresentation, String> getter,
            BiConsumer<RealmRepresentation, String> setter) {}

    private static final RealmBinding[] REALM_BINDINGS = {
        new RealmBinding("browser", RealmRepresentation::getBrowserFlow, RealmRepresentation::setBrowserFlow),
        new RealmBinding(
                "direct grant", RealmRepresentation::getDirectGrantFlow, RealmRepresentation::setDirectGrantFlow),
        new RealmBinding(
                "client authentication",
                RealmRepresentation::getClientAuthenticationFlow,
                RealmRepresentation::setClientAuthenticationFlow),
        new RealmBinding(
                "docker authentication",
                RealmRepresentation::getDockerAuthenticationFlow,
                RealmRepresentation::setDockerAuthenticationFlow),
        new RealmBinding(
                "registration", RealmRepresentation::getRegistrationFlow, RealmRepresentation::setRegistrationFlow),
        new RealmBinding(
                "reset credentials",
                RealmRepresentation::getResetCredentialsFlow,
                RealmRepresentation::setResetCredentialsFlow),
    };

    private final String realm;
    private final AuthenticationFlowApi flowApi;
    private final ClientApi clientApi;
    private final IdentityProviderApi identityProviderApi;

    private final Map<RealmBinding, String> realmBindings = new HashMap<>();
    private final Map<String, String> firstBrokerLoginFlows = new HashMap<>();
    private final Map<String, String> postBrokerLoginFlows = new HashMap<>();

    UsedFlowWorkaround(
            final String realm,
            final AuthenticationFlowApi flowApi,
            final ClientApi clientApi,
            final IdentityProviderApi identityProviderApi) {
        this.realm = realm;
        this.flowApi = flowApi;
        this.clientApi = clientApi;
        this.identityProviderApi = identityProviderApi;
    }

    /** Moves the realm bindings and the identity providers using the flow to the temporary flow. */
    void disableTopLevelFlowIfNeeded(final String alias) {
        RealmRepresentation existing = this.flowApi.getRealm(this.realm);
        for (RealmBinding binding : REALM_BINDINGS) {
            if (Objects.equals(binding.getter().apply(existing), alias)) {
                logger.debug(
                        "Temporary disable {} flow in realm '{}' which is '{}'", binding.name(), this.realm, alias);
                this.realmBindings.put(binding, alias);
                binding.setter().accept(existing, temporaryFlow());
                this.flowApi.updateRealm(this.realm, existing);
            }
        }
        for (IdentityProviderRepresentation identityProvider :
                this.identityProviderApi.listIdentityProviders(this.realm)) {
            boolean changed = false;
            if (Objects.equals(identityProvider.getFirstBrokerLoginFlowAlias(), alias)) {
                this.firstBrokerLoginFlows.put(identityProvider.getAlias(), alias);
                identityProvider.setFirstBrokerLoginFlowAlias(temporaryFlow());
                changed = true;
            }
            if (Objects.equals(identityProvider.getPostBrokerLoginFlowAlias(), alias)) {
                this.postBrokerLoginFlows.put(identityProvider.getAlias(), alias);
                identityProvider.setPostBrokerLoginFlowAlias(temporaryFlow());
                changed = true;
            }
            if (changed) {
                logger.debug(
                        "Temporary disable flow of identity-provider '{}' in realm '{}' which is '{}'",
                        identityProvider.getAlias(),
                        this.realm,
                        alias);
                this.identityProviderApi.updateIdentityProvider(
                        this.realm, identityProvider.getAlias(), identityProvider);
            }
        }
    }

    /**
     * Binds the clients overriding a binding with the flow to a temporary copy of it.
     *
     * @return the overridden bindings, by client id
     */
    Map<String, Set<String>> removeFlowOverridesInClients(
            final String flowId, final AuthenticationFlowRepresentation flow) {
        Map<String, Set<String>> overridden = new HashMap<>();
        for (ClientRepresentation client : this.clientApi.listClients(this.realm)) {
            Map<String, String> overrides = client.getAuthenticationFlowBindingOverrides();
            if (overrides == null || !overrides.containsValue(flowId)) {
                continue;
            }
            Set<String> bindings = new LinkedHashSet<>();
            String temporary = temporaryClientFlow(flow);
            overrides.replaceAll((binding, id) -> {
                if (flowId.equals(id)) {
                    bindings.add(binding);
                    return temporary;
                }
                return id;
            });
            overridden.put(client.getId(), bindings);
            this.clientApi.updateClient(this.realm, client.getId(), client);
        }
        return overridden;
    }

    /** Binds the clients back to the recreated flow, and deletes the temporary copy. */
    void restoreClientOverrides(final Map<String, Set<String>> overridden, final String recreatedFlowId) {
        if (overridden.isEmpty()) {
            return;
        }
        Map<String, ClientRepresentation> clients = new HashMap<>();
        this.clientApi.listClients(this.realm).forEach(client -> clients.put(client.getId(), client));
        overridden.forEach((clientId, bindings) -> {
            ClientRepresentation client = clients.get(clientId);
            if (client == null) {
                return;
            }
            Map<String, String> overrides = new HashMap<>(client.getAuthenticationFlowBindingOverrides());
            bindings.forEach(binding -> overrides.put(binding, recreatedFlowId));
            client.setAuthenticationFlowBindingOverrides(overrides);
            this.clientApi.updateClient(this.realm, clientId, client);
        });
        findTopLevelFlow(TEMPORARY_CLIENT_FLOW).ifPresent(flow -> this.flowApi.deleteFlow(this.realm, flow.getId()));
    }

    /** Binds the realm and the identity providers back to the recreated flow, and deletes the temporary flow. */
    void resetFlowIfNeeded() {
        if (this.realmBindings.isEmpty()
                && this.firstBrokerLoginFlows.isEmpty()
                && this.postBrokerLoginFlows.isEmpty()) {
            return;
        }
        if (!this.realmBindings.isEmpty()) {
            RealmRepresentation existing = this.flowApi.getRealm(this.realm);
            this.realmBindings.forEach((binding, alias) -> binding.setter().accept(existing, alias));
            this.flowApi.updateRealm(this.realm, existing);
        }
        this.firstBrokerLoginFlows.forEach((identityProvider, alias) -> this.identityProviderApi
                .findIdentityProvider(this.realm, identityProvider)
                .ifPresent(existing -> {
                    existing.setFirstBrokerLoginFlowAlias(alias);
                    this.identityProviderApi.updateIdentityProvider(this.realm, identityProvider, existing);
                }));
        this.postBrokerLoginFlows.forEach((identityProvider, alias) -> this.identityProviderApi
                .findIdentityProvider(this.realm, identityProvider)
                .ifPresent(existing -> {
                    existing.setPostBrokerLoginFlowAlias(alias);
                    this.identityProviderApi.updateIdentityProvider(this.realm, identityProvider, existing);
                }));
        if (!isTemporaryFlowUsedByRealm()) {
            logger.debug("Delete temporary created top-level-flow '{}' in realm '{}'", TEMPORARY_FLOW, this.realm);
            findTopLevelFlow(TEMPORARY_FLOW).ifPresent(flow -> this.flowApi.deleteFlow(this.realm, flow.getId()));
        }
    }

    private boolean isTemporaryFlowUsedByRealm() {
        RealmRepresentation existing = this.flowApi.getRealm(this.realm);
        for (RealmBinding binding : REALM_BINDINGS) {
            if (TEMPORARY_FLOW.equals(binding.getter().apply(existing))) {
                return true;
            }
        }
        return false;
    }

    private String temporaryFlow() {
        if (findTopLevelFlow(TEMPORARY_FLOW).isEmpty()) {
            logger.debug("Create top-level-flow '{}' in realm '{}' to be used temporarily", TEMPORARY_FLOW, this.realm);
            AuthenticationFlowRepresentation temporary = new AuthenticationFlowRepresentation();
            temporary.setAlias(TEMPORARY_FLOW);
            temporary.setTopLevel(true);
            temporary.setBuiltIn(false);
            temporary.setProviderId(TEMPORARY_FLOW);
            this.flowApi.createTopLevelFlow(this.realm, temporary);
        }
        return TEMPORARY_FLOW;
    }

    private String temporaryClientFlow(final AuthenticationFlowRepresentation flow) {
        Optional<AuthenticationFlowRepresentation> existing = findTopLevelFlow(TEMPORARY_CLIENT_FLOW);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        AuthenticationFlowRepresentation temporary = new AuthenticationFlowRepresentation();
        temporary.setAlias(TEMPORARY_CLIENT_FLOW);
        temporary.setProviderId(TEMPORARY_CLIENT_FLOW);
        temporary.setDescription(flow.getDescription());
        temporary.setTopLevel(true);
        temporary.setBuiltIn(false);
        return this.flowApi.createTopLevelFlow(this.realm, temporary);
    }

    private Optional<AuthenticationFlowRepresentation> findTopLevelFlow(final String alias) {
        return this.flowApi.listFlows(this.realm).stream()
                .filter(flow -> Objects.equals(flow.getAlias(), alias))
                .findFirst();
    }
}
