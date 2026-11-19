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

import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.InvalidAuthenticationFlowException;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.springframework.stereotype.Component;

/**
 * Conversions between the Keycloak representations of authentication flows and their business objects (ADR-006). The
 * definition of a flow is its representation without its id and alias, and without the built-in flag of a top-level
 * flow, carried by the business object.
 */
@Component
public class AuthenticationFlowKeycloakMapper {

    static final String FORM_FLOW = "form-flow";

    private static final Set<String> TOP_LEVEL_NOT_DEFINITION = Set.of("id", "alias", "builtIn");
    private static final Set<String> SUB_FLOW_NOT_DEFINITION = Set.of("id", "alias");

    private final UnmodeledAttributesConverter unmodeledAttributes;

    public AuthenticationFlowKeycloakMapper(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /**
     * Flows declared by an import file: each built-in flow, and each other top-level flow with its tree.
     *
     * @param configs authenticator configs declared by the file, {@code null} when none
     * @throws InvalidAuthenticationFlowException when an execution refers to a sub-flow the file does not declare, or
     *     sets an authenticator on a sub-flow that is not a form
     */
    public List<AuthenticationFlow> toDesiredFlows(
            final List<AuthenticationFlowRepresentation> flows, final List<AuthenticatorConfigRepresentation> configs) {
        Map<String, AuthenticationFlowRepresentation> subFlows = flows.stream()
                .filter(flow -> !flow.isTopLevel())
                .collect(Collectors.toMap(
                        AuthenticationFlowRepresentation::getAlias,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        Map<String, AuthenticatorConfig> declaredConfigs = new LinkedHashMap<>();
        if (configs != null) {
            configs.forEach(
                    config -> declaredConfigs.putIfAbsent(config.getAlias(), toAuthenticatorConfig(config, false)));
        }
        List<AuthenticationFlow> desired = new ArrayList<>();
        for (AuthenticationFlowRepresentation flow : flows) {
            if (flow.isBuiltIn()) {
                desired.add(new AuthenticationFlow(
                        flow.getAlias(),
                        flow.getId(),
                        true,
                        definitionOf(flow),
                        Map.of(),
                        configsOf(List.of(flow), declaredConfigs)));
            } else if (flow.isTopLevel()) {
                Map<String, AuthenticationFlowRepresentation> tree = new LinkedHashMap<>();
                collectDeclaredTree(flow, subFlows, tree);
                List<AuthenticationFlowRepresentation> all = new ArrayList<>();
                all.add(flow);
                all.addAll(tree.values());
                desired.add(new AuthenticationFlow(
                        flow.getAlias(),
                        flow.getId(),
                        false,
                        definitionOf(flow),
                        subFlowDefinitions(tree),
                        configsOf(all, declaredConfigs)));
            }
        }
        return desired;
    }

    @SuppressWarnings("deprecation")
    private static void collectDeclaredTree(
            final AuthenticationFlowRepresentation flow,
            final Map<String, AuthenticationFlowRepresentation> subFlows,
            final Map<String, AuthenticationFlowRepresentation> tree) {
        for (AuthenticationExecutionExportRepresentation execution : executionsOf(flow)) {
            if (!execution.isAutheticatorFlow()) {
                continue;
            }
            AuthenticationFlowRepresentation subFlow = subFlows.get(execution.getFlowAlias());
            if (subFlow == null) {
                throw new InvalidAuthenticationFlowException(
                        "Non-toplevel flow not found: " + execution.getFlowAlias());
            }
            if (execution.getAuthenticator() != null && !Objects.equals(subFlow.getProviderId(), FORM_FLOW)) {
                throw new InvalidAuthenticationFlowException(String.format(
                        "Execution property authenticator '%s' can be only set if the sub-flow '%s' type is"
                                + " 'form-flow'.",
                        execution.getAuthenticator(), subFlow.getAlias()));
            }
            if (tree.putIfAbsent(subFlow.getAlias(), subFlow) == null) {
                collectDeclaredTree(subFlow, subFlows, tree);
            }
        }
    }

    /**
     * Flows of a realm in Keycloak: each top-level flow, with its tree unless it is built-in, and each built-in flow
     * that is not top-level.
     */
    public List<AuthenticationFlow> toCurrentFlows(final List<AuthenticationFlowRepresentation> flows) {
        Map<String, AuthenticationFlowRepresentation> byAlias = flows.stream()
                .collect(Collectors.toMap(
                        AuthenticationFlowRepresentation::getAlias,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        List<AuthenticationFlow> current = new ArrayList<>();
        for (AuthenticationFlowRepresentation flow : flows) {
            if (flow.isBuiltIn()) {
                current.add(new AuthenticationFlow(
                        flow.getAlias(), flow.getId(), true, definitionOf(flow), Map.of(), Map.of()));
            } else if (flow.isTopLevel()) {
                Map<String, AuthenticationFlowRepresentation> tree = new LinkedHashMap<>();
                collectCurrentTree(flow, byAlias, tree);
                current.add(new AuthenticationFlow(
                        flow.getAlias(), flow.getId(), false, definitionOf(flow), subFlowDefinitions(tree), Map.of()));
            }
        }
        return current;
    }

    @SuppressWarnings("deprecation")
    private static void collectCurrentTree(
            final AuthenticationFlowRepresentation flow,
            final Map<String, AuthenticationFlowRepresentation> byAlias,
            final Map<String, AuthenticationFlowRepresentation> tree) {
        for (AuthenticationExecutionExportRepresentation execution : executionsOf(flow)) {
            AuthenticationFlowRepresentation subFlow =
                    execution.isAutheticatorFlow() ? byAlias.get(execution.getFlowAlias()) : null;
            if (subFlow != null && tree.putIfAbsent(subFlow.getAlias(), subFlow) == null) {
                collectCurrentTree(subFlow, byAlias, tree);
            }
        }
    }

    static List<AuthenticationExecutionExportRepresentation> executionsOf(final AuthenticationFlowRepresentation flow) {
        return flow.getAuthenticationExecutions() == null ? List.of() : flow.getAuthenticationExecutions();
    }

    private UnmodeledAttributes definitionOf(final AuthenticationFlowRepresentation flow) {
        return this.unmodeledAttributes.extract(
                flow, flow.isTopLevel() ? TOP_LEVEL_NOT_DEFINITION : SUB_FLOW_NOT_DEFINITION);
    }

    private Map<String, UnmodeledAttributes> subFlowDefinitions(
            final Map<String, AuthenticationFlowRepresentation> tree) {
        Map<String, UnmodeledAttributes> definitions = new LinkedHashMap<>();
        tree.forEach((alias, subFlow) ->
                definitions.put(alias, this.unmodeledAttributes.extract(subFlow, SUB_FLOW_NOT_DEFINITION)));
        return definitions;
    }

    private static Map<String, AuthenticatorConfig> configsOf(
            final List<AuthenticationFlowRepresentation> flows, final Map<String, AuthenticatorConfig> declared) {
        Map<String, AuthenticatorConfig> used = new LinkedHashMap<>();
        flows.forEach(flow -> executionsOf(flow).stream()
                .map(AuthenticationExecutionExportRepresentation::getAuthenticatorConfig)
                .filter(Objects::nonNull)
                .filter(declared::containsKey)
                .forEach(alias -> used.put(alias, declared.get(alias))));
        return used;
    }

    /** A flow as Keycloak expects it, without its id. */
    public AuthenticationFlowRepresentation toRepresentation(final AuthenticationFlow flow) {
        AuthenticationFlowRepresentation representation = new AuthenticationFlowRepresentation();
        this.unmodeledAttributes.apply(flow.definition(), representation);
        representation.setId(flow.id());
        representation.setAlias(flow.alias());
        representation.setBuiltIn(flow.builtIn());
        return representation;
    }

    /** The sub-flows of a flow as Keycloak expects them, by alias. */
    public Map<String, AuthenticationFlowRepresentation> subFlowsOf(final AuthenticationFlow flow) {
        Map<String, AuthenticationFlowRepresentation> subFlows = new LinkedHashMap<>();
        flow.subFlows().forEach((alias, definition) -> {
            AuthenticationFlowRepresentation representation = new AuthenticationFlowRepresentation();
            this.unmodeledAttributes.apply(definition, representation);
            representation.setAlias(alias);
            subFlows.put(alias, representation);
        });
        return subFlows;
    }

    public AuthenticatorConfig toAuthenticatorConfig(
            final AuthenticatorConfigRepresentation representation, final boolean inUse) {
        return new AuthenticatorConfig(representation.getAlias(), representation.getConfig(), inUse);
    }

    public AuthenticatorConfigRepresentation toRepresentation(final AuthenticatorConfig config, final String id) {
        AuthenticatorConfigRepresentation representation = new AuthenticatorConfigRepresentation();
        representation.setId(id);
        representation.setAlias(config.alias());
        representation.setConfig(new LinkedHashMap<>(config.config()));
        return representation;
    }
}
