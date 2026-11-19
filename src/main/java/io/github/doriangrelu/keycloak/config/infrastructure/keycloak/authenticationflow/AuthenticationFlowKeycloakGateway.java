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

import io.github.doriangrelu.keycloak.config.application.authenticationflow.AuthenticationFlowRejectedException;
import io.github.doriangrelu.keycloak.config.application.authenticationflow.UnknownAuthenticatorConfigException;
import io.github.doriangrelu.keycloak.config.application.authenticationflow.port.AuthenticationFlowGateway;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.InvalidAuthenticationFlowException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.AuthenticationFlowApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link AuthenticationFlowGateway} on the Keycloak Admin API. Keycloak builds a flow one execution at a time: a
 * top-level execution is added with its requirement, while an execution of a sub-flow is added first, then given its
 * requirement.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class AuthenticationFlowKeycloakGateway implements AuthenticationFlowGateway {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFlowKeycloakGateway.class);

    private static final Set<KeycloakFailure> REFUSALS = Set.of(
            KeycloakFailure.REJECTED, KeycloakFailure.NOT_FOUND, KeycloakFailure.CONFLICT, KeycloakFailure.UNEXPECTED);

    private final AuthenticationFlowApi flowApi;
    private final ClientApi clientApi;
    private final IdentityProviderApi identityProviderApi;
    private final AuthenticationFlowKeycloakMapper mapper;

    public AuthenticationFlowKeycloakGateway(
            final AuthenticationFlowApi flowApi,
            final ClientApi clientApi,
            final IdentityProviderApi identityProviderApi,
            final AuthenticationFlowKeycloakMapper mapper) {
        this.flowApi = flowApi;
        this.clientApi = clientApi;
        this.identityProviderApi = identityProviderApi;
        this.mapper = mapper;
    }

    @Override
    public List<AuthenticationFlow> findAll(final String realm) {
        return this.mapper.toCurrentFlows(this.flowApi.listFlows(realm));
    }

    private Optional<AuthenticationFlowRepresentation> findFlow(final String realm, final String alias) {
        return this.flowApi.listFlows(realm).stream()
                .filter(flow -> Objects.equals(flow.getAlias(), alias))
                .findFirst();
    }

    @Override
    public void create(final String realm, final AuthenticationFlow flow) {
        logger.debug("Creating top-level flow: {}", flow.alias());
        AuthenticationFlowRepresentation topLevel = this.mapper.toRepresentation(flow);
        AuthenticationFlowRepresentation withoutExecutions = this.mapper.toRepresentation(flow);
        withoutExecutions.setAuthenticationExecutions(null);
        String id = refusing(
                () -> this.flowApi.createTopLevelFlow(realm, withoutExecutions),
                error -> String.format(
                        "Cannot create top-level-flow '%s' in realm '%s': %s",
                        flow.alias(), realm, error.getMessage()));
        new Builder(realm, flow).createExecutions(topLevel, id);
    }

    @Override
    public void recreate(final String realm, final AuthenticationFlow current, final AuthenticationFlow target) {
        logger.debug("Recreate top-level flow: {}", target.alias());
        AuthenticationFlowRepresentation existing = findFlow(realm, target.alias())
                .orElseThrow(() ->
                        new IllegalStateException("Flow '" + target.alias() + "' is missing in realm '" + realm + "'"));
        UsedFlowWorkaround workaround =
                new UsedFlowWorkaround(realm, this.flowApi, this.clientApi, this.identityProviderApi);
        workaround.disableTopLevelFlowIfNeeded(target.alias());
        Map<String, Set<String>> overrides =
                workaround.removeFlowOverridesInClients(existing.getId(), this.mapper.toRepresentation(target));
        deleteAuthenticatorConfigsOf(realm, target.alias());
        this.flowApi.deleteFlow(realm, existing.getId());
        create(realm, target);
        String createdId = findFlow(realm, target.alias())
                .map(AuthenticationFlowRepresentation::getId)
                .orElseThrow();
        workaround.restoreClientOverrides(overrides, createdId);
        workaround.resetFlowIfNeeded();
    }

    private void deleteAuthenticatorConfigsOf(final String realm, final String alias) {
        this.flowApi.listExecutions(realm, alias).stream()
                .map(AuthenticationExecutionInfoRepresentation::getAuthenticationConfig)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(id -> {
                    logger.debug("Delete authenticator config: '{}'", id);
                    this.flowApi.deleteAuthenticatorConfig(realm, id);
                });
    }

    @Override
    public void patchBuiltIn(final String realm, final AuthenticationFlow target) {
        logger.debug("Updating builtin flow: {}", target.alias());
        AuthenticationFlowRepresentation existing = findFlow(realm, target.alias())
                .orElseThrow(() ->
                        new IllegalStateException("Flow '" + target.alias() + "' is missing in realm '" + realm + "'"));
        AuthenticationFlowRepresentation patched = this.mapper.toRepresentation(target);
        patched.setId(existing.getId());
        try {
            this.flowApi.updateFlow(realm, existing.getId(), patched);
        } catch (final KeycloakAccessException error) {
            throw rejected(
                    error,
                    () -> new AuthenticationFlowRejectedException(
                            String.format("Cannot update top-level-flow '%s' in realm '%s'.", target.alias(), realm),
                            error));
        }
        Builder builder = new Builder(realm, target);
        AuthenticationFlowKeycloakMapper.executionsOf(patched)
                .forEach(execution -> builder.configureExecution(target.alias(), execution));
    }

    @Override
    public void delete(final String realm, final String alias) {
        boolean usedByIdentityProvider = this.identityProviderApi.listIdentityProviders(realm).stream()
                .anyMatch(idp -> alias.equals(idp.getFirstBrokerLoginFlowAlias())
                        || alias.equals(idp.getPostBrokerLoginFlowAlias()));
        if (usedByIdentityProvider) {
            logger.warn("Cannot delete authentication flow '{}' as it is referenced by an identity provider", alias);
            return;
        }
        Optional<AuthenticationFlowRepresentation> existing = findFlow(realm, alias);
        if (existing.isPresent()) {
            logger.debug("Delete authentication flow: {}", alias);
            this.flowApi.deleteFlow(realm, existing.get().getId());
        }
    }

    @Override
    public List<AuthenticatorConfig> findAllAuthenticatorConfigs(final String realm) {
        Set<String> used = this.flowApi.listFlows(realm).stream()
                .flatMap(flow -> AuthenticationFlowKeycloakMapper.executionsOf(flow).stream())
                .map(AuthenticationExecutionExportRepresentation::getAuthenticatorConfig)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, AuthenticatorConfig> byAlias = new LinkedHashMap<>();
        this.flowApi
                .listAuthenticatorConfigs(realm)
                .forEach(config -> byAlias.putIfAbsent(
                        config.getAlias(),
                        this.mapper.toAuthenticatorConfig(config, used.contains(config.getAlias()))));
        return List.copyOf(byAlias.values());
    }

    @Override
    public void updateAuthenticatorConfig(final String realm, final AuthenticatorConfig config) {
        configsWithAlias(realm, config.alias())
                .forEach(existing -> this.flowApi.updateAuthenticatorConfig(
                        realm, existing.getId(), this.mapper.toRepresentation(config, existing.getId())));
    }

    @Override
    public void deleteAuthenticatorConfig(final String realm, final String alias) {
        configsWithAlias(realm, alias).forEach(existing -> {
            logger.debug("Delete authenticator config: {}", alias);
            this.flowApi.deleteAuthenticatorConfig(realm, existing.getId());
        });
    }

    private List<AuthenticatorConfigRepresentation> configsWithAlias(final String realm, final String alias) {
        return this.flowApi.listAuthenticatorConfigs(realm).stream()
                .filter(config -> Objects.equals(config.getAlias(), alias))
                .toList();
    }

    @Override
    public Optional<ClientFlowBindings> findClientFlowBindings(final String realm, final ClientFlowBindings client) {
        Map<String, String> aliasesById = this.flowApi.listFlows(realm).stream()
                .collect(Collectors.toMap(
                        AuthenticationFlowRepresentation::getId,
                        AuthenticationFlowRepresentation::getAlias,
                        (first, second) -> first));
        return findClient(realm, client).map(existing -> {
            Map<String, String> overrides = new LinkedHashMap<>();
            if (existing.getAuthenticationFlowBindingOverrides() != null) {
                existing.getAuthenticationFlowBindingOverrides()
                        .forEach((binding, id) -> overrides.put(binding, aliasesById.getOrDefault(id, id)));
            }
            return client.withOverrides(overrides);
        });
    }

    @Override
    public void saveClientFlowBindings(final String realm, final ClientFlowBindings bindings) {
        ClientRepresentation existing = findClient(realm, bindings)
                .orElseThrow(() -> new IllegalStateException(
                        "Client '" + bindings.key() + "' is missing in realm '" + realm + "'"));
        List<AuthenticationFlowRepresentation> flows = this.flowApi.listFlows(realm);
        Map<String, String> updates = new HashMap<>();
        // Keycloak only removes a binding set to null
        if (existing.getAuthenticationFlowBindingOverrides() != null) {
            existing.getAuthenticationFlowBindingOverrides().keySet().forEach(binding -> updates.put(binding, null));
        }
        bindings.overrides().forEach((binding, flow) -> updates.put(binding, flowIdOf(realm, flows, flow)));
        existing.setAuthenticationFlowBindingOverrides(updates);
        this.clientApi.updateClient(realm, existing.getId(), existing);
    }

    private static String flowIdOf(
            final String realm, final List<AuthenticationFlowRepresentation> flows, final String aliasOrId) {
        if (aliasOrId == null || aliasOrId.isEmpty()) {
            return aliasOrId;
        }
        if (flows.stream().anyMatch(flow -> aliasOrId.equals(flow.getId()))) {
            return aliasOrId;
        }
        return flows.stream()
                .filter(flow -> aliasOrId.equals(flow.getAlias()))
                .map(AuthenticationFlowRepresentation::getId)
                .findFirst()
                .orElseThrow(() -> new InvalidAuthenticationFlowException(
                        String.format("Cannot find top-level-flow '%s' in realm '%s'.", aliasOrId, realm)));
    }

    private Optional<ClientRepresentation> findClient(final String realm, final ClientFlowBindings client) {
        if (client.clientId() != null) {
            return this.clientApi.findByClientId(realm, client.clientId());
        }
        return this.clientApi.listClients(realm).stream()
                .filter(candidate -> Objects.equals(candidate.getName(), client.name()))
                .findFirst();
    }

    private static <T> T refusing(
            final Supplier<T> call, final java.util.function.Function<KeycloakAccessException, String> message) {
        try {
            return call.get();
        } catch (final KeycloakAccessException error) {
            throw rejected(error, () -> new AuthenticationFlowRejectedException(message.apply(error), error));
        }
    }

    private static RuntimeException rejected(
            final KeycloakAccessException error, final Supplier<RuntimeException> translation) {
        return REFUSALS.contains(error.getFailure()) ? translation.get() : error;
    }

    /** Creates the executions of a flow, and configures them, as declared by the import. */
    private final class Builder {

        private final String realm;
        private final AuthenticationFlow flow;
        private final Map<String, AuthenticationFlowRepresentation> subFlows;

        private Builder(final String realm, final AuthenticationFlow flow) {
            this.realm = realm;
            this.flow = flow;
            this.subFlows = AuthenticationFlowKeycloakGateway.this.mapper.subFlowsOf(flow);
        }

        @SuppressWarnings("deprecation")
        void createExecutions(final AuthenticationFlowRepresentation topLevel, final String topLevelId) {
            for (AuthenticationExecutionExportRepresentation execution :
                    AuthenticationFlowKeycloakMapper.executionsOf(topLevel)) {
                if (execution.isAutheticatorFlow()) {
                    createAndConfigureSubFlow(topLevel.getAlias(), execution);
                } else {
                    createTopLevelExecution(topLevel.getAlias(), topLevelId, execution);
                }
            }
        }

        @SuppressWarnings("deprecation")
        private void createTopLevelExecution(
                final String topLevelAlias,
                final String topLevelId,
                final AuthenticationExecutionExportRepresentation execution) {
            logger.debug(
                    "Creating execution '{}' for top-level-flow: '{}' in realm '{}'",
                    execution.getAuthenticator(),
                    topLevelAlias,
                    this.realm);
            AuthenticationExecutionRepresentation created = new AuthenticationExecutionRepresentation();
            created.setParentFlow(topLevelId);
            created.setAuthenticator(execution.getAuthenticator());
            created.setRequirement(execution.getRequirement());
            created.setPriority(execution.getPriority());
            created.setAutheticatorFlow(false);
            String executionId = refusing(
                    () -> AuthenticationFlowKeycloakGateway.this.flowApi.addExecution(this.realm, created),
                    error -> String.format(
                            "Cannot create execution-flow '%s' for top-level-flow '%s' in realm '%s'",
                            execution.getAuthenticator(), topLevelAlias, this.realm));
            if (execution.getAuthenticatorConfig() != null) {
                createAuthenticatorConfig(execution.getAuthenticatorConfig(), executionId);
            }
        }

        @SuppressWarnings("deprecation")
        private void createAndConfigureSubFlow(
                final String parentAlias, final AuthenticationExecutionExportRepresentation execution) {
            AuthenticationFlowRepresentation subFlow = this.subFlows.get(execution.getFlowAlias());
            logger.debug(
                    "Creating non-top-level-flow '{}' for flow '{}' in realm '{}'",
                    execution.getFlowAlias(),
                    parentAlias,
                    this.realm);
            Map<String, Object> data = new HashMap<>();
            data.put("alias", execution.getFlowAlias());
            data.put("provider", execution.getAuthenticator());
            data.put("type", subFlow.getProviderId());
            data.put("description", subFlow.getDescription());
            data.put("authenticator", subFlow.getProviderId());
            refusing(
                    () -> {
                        AuthenticationFlowKeycloakGateway.this.flowApi.addSubFlow(this.realm, parentAlias, data);
                        return null;
                    },
                    error -> String.format(
                            "Cannot create execution-flow '%s' for top-level-flow '%s' in realm '%s': %s",
                            execution.getFlowAlias(), parentAlias, this.realm, error.getMessage()));
            configureExecution(parentAlias, execution);
            for (AuthenticationExecutionExportRepresentation subExecution :
                    AuthenticationFlowKeycloakMapper.executionsOf(subFlow)) {
                if (subExecution.isAutheticatorFlow()) {
                    createAndConfigureSubFlow(subFlow.getAlias(), subExecution);
                } else {
                    createSubFlowExecution(subFlow.getAlias(), subExecution);
                    configureExecution(subFlow.getAlias(), subExecution);
                }
            }
        }

        private void createSubFlowExecution(
                final String subFlowAlias, final AuthenticationExecutionExportRepresentation execution) {
            logger.debug(
                    "Create execution '{}' for non-top-level-flow '{}' in realm '{}'",
                    execution.getAuthenticator(),
                    subFlowAlias,
                    this.realm);
            Map<String, Object> data = new HashMap<>();
            data.put("provider", execution.getAuthenticator());
            refusing(
                    () -> {
                        AuthenticationFlowKeycloakGateway.this.flowApi.addExecutionToFlow(
                                this.realm, subFlowAlias, data);
                        return null;
                    },
                    error -> String.format(
                            "Cannot create execution '%s' for non-top-level-flow '%s' in realm '%s': %s",
                            execution.getAuthenticator(), subFlowAlias, this.realm, error.getMessage()));
            if (execution.getAuthenticatorConfig() != null) {
                List<AuthenticationExecutionInfoRepresentation> withoutConfig =
                        storedExecutions(subFlowAlias, execution).stream()
                                .filter(stored -> stored.getAuthenticationConfig() == null)
                                .toList();
                if (withoutConfig.size() != 1) {
                    throw new AuthenticationFlowRejectedException(
                            String.format(
                                    "Unexpected size of execution %s in flow %s. Expected: 1. Actual: %d",
                                    execution.getAuthenticator(), subFlowAlias, withoutConfig.size()),
                            null);
                }
                createAuthenticatorConfig(
                        execution.getAuthenticatorConfig(),
                        withoutConfig.getFirst().getId());
            }
        }

        /** Sets the declared requirement of a stored execution, and creates its declared config if it has none. */
        void configureExecution(final String flowAlias, final AuthenticationExecutionExportRepresentation execution) {
            List<AuthenticationExecutionInfoRepresentation> stored = storedExecutions(flowAlias, execution);
            if (stored.size() != 1) {
                throw new AuthenticationFlowRejectedException(
                        String.format(
                                "Unexpected size of execution %s in flow %s found.",
                                execution.getAuthenticator(), flowAlias),
                        null);
            }
            AuthenticationExecutionInfoRepresentation storedExecution = stored.getFirst();
            storedExecution.setRequirement(execution.getRequirement());
            refusing(
                    () -> {
                        AuthenticationFlowKeycloakGateway.this.flowApi.updateExecution(
                                this.realm, flowAlias, storedExecution);
                        return null;
                    },
                    error -> String.format(
                            "Cannot update execution-flow '%s' for flow '%s' in realm '%s': %s",
                            execution.getAuthenticator(), flowAlias, this.realm, error.getMessage()));
            if (storedExecution.getAuthenticationConfig() == null && execution.getAuthenticatorConfig() != null) {
                createAuthenticatorConfig(execution.getAuthenticatorConfig(), storedExecution.getId());
            } else if (storedExecution.getAuthenticationConfig() != null
                    && execution.getAuthenticatorConfig() == null) {
                // The legacy import deleted the configs no declared execution uses
                logger.debug("Delete authenticator config: '{}'", storedExecution.getAuthenticationConfig());
                AuthenticationFlowKeycloakGateway.this.flowApi.deleteAuthenticatorConfig(
                        this.realm, storedExecution.getAuthenticationConfig());
            }
        }

        private List<AuthenticationExecutionInfoRepresentation> storedExecutions(
                final String flowAlias, final AuthenticationExecutionExportRepresentation execution) {
            List<AuthenticationExecutionInfoRepresentation> found =
                    AuthenticationFlowKeycloakGateway.this.flowApi.listExecutions(this.realm, flowAlias).stream()
                            .filter(stored -> Objects.equals(stored.getProviderId(), execution.getAuthenticator()))
                            .filter(stored -> execution.getAuthenticatorConfig() == null
                                    || stored.getAlias() == null
                                    || Objects.equals(stored.getAlias(), execution.getAuthenticatorConfig()))
                            .filter(stored -> execution.getFlowAlias() == null
                                    || Objects.equals(stored.getDisplayName(), execution.getFlowAlias()))
                            .toList();
            if (found.isEmpty()) {
                String withSubFlow =
                        execution.getFlowAlias() != null ? "' or flow by alias '" + execution.getFlowAlias() : "";
                throw new AuthenticationFlowRejectedException(
                        String.format(
                                "Cannot find stored execution by authenticator '%s%s' in top-level flow '%s' in realm"
                                        + " '%s'",
                                execution.getAuthenticator(), withSubFlow, flowAlias, this.realm),
                        null);
            }
            return found;
        }

        private void createAuthenticatorConfig(final String alias, final String executionId) {
            AuthenticatorConfig config = this.flow.authenticatorConfigs().get(alias);
            if (config == null) {
                throw UnknownAuthenticatorConfigException.notDeclared(alias);
            }
            try {
                AuthenticationFlowKeycloakGateway.this.flowApi.createExecutionConfig(
                        this.realm,
                        executionId,
                        AuthenticationFlowKeycloakGateway.this.mapper.toRepresentation(config, null));
            } catch (final KeycloakAccessException error) {
                // Keycloak refuses a second config with the same alias; the legacy import ignored it too
                if (error.getFailure() != KeycloakFailure.CONFLICT) {
                    throw error;
                }
                logger.warn(
                        "Authenticator config '{}' already exists in realm '{}': not added to execution '{}'",
                        alias,
                        this.realm,
                        executionId);
            }
        }
    }
}
