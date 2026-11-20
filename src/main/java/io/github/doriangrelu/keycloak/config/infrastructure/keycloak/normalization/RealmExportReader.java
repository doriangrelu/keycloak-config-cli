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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.authenticationflow.AuthenticationFlowKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy.ClientPolicyKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.component.ComponentKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group.GroupKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider.IdentityProviderKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm.RealmKeycloakGateway;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm.RealmKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction.RequiredActionKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role.RoleKeycloakMapper;
import io.github.doriangrelu.keycloak.config.util.CloneUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClientPoliciesRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the resources of an exported realm, or of a reference realm, for the normalization. Unlike an import file, an
 * export is read as it is, without any call to Keycloak, and is cleaned up first:
 *
 * <ul>
 *   <li>the Keycloak ids are left out, so that the export and the reference can be compared, except the ones the
 *       export refers to: the flow bindings of the clients are given by alias;
 *   <li>key providers are left out: they hold the keys of the realm, generated by Keycloak; so is the user profile,
 *       a component without name that an import declares on its own;
 *   <li>OpenID Connect clients lose the SAML attributes older Keycloak versions give them;
 *   <li>what an import does not handle is left out: users, the default role, the deprecated fields.
 * </ul>
 */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "run",
        name = "operation",
        havingValue = "NORMALIZE")
class RealmExportReader {

    private static final Logger logger = LoggerFactory.getLogger(RealmExportReader.class);

    static final String KEY_PROVIDER = "org.keycloak.keys.KeyProvider";

    static final String USER_PROFILE_PROVIDER = "org.keycloak.userprofile.UserProfileProvider";

    /** Settings of an export that are not normalized. */
    private static final Set<String> IGNORED_SETTINGS = Set.of(
            "defaultRole",
            "users",
            "federatedUsers",
            "applicationScopeMappings",
            "applications",
            "oauthClients",
            "clientTemplates");

    private static final Set<String> SAML_ATTRIBUTES = Set.of(
            "saml_signature_canonicalization_method",
            "saml.onetimeuse.condition",
            "saml_name_id_format",
            "saml.authnstatement",
            "saml.server.signature.keyinfo$xmlSigKeyInfoKeyNameTransformer",
            "saml_force_name_id_format",
            "saml.artifact.binding",
            "saml.artifact.binding.identifier",
            "saml.server.signature",
            "saml.encrypt",
            "saml.assertion.signature",
            "saml.allow.ecp.flow",
            "saml.signing.private.key",
            "saml.force.name.id.format",
            "saml.client.signature",
            "saml.signature.algorithm",
            "saml.signing.certificate",
            "saml.server.signature.keyinfo.ext",
            "saml.multivalued.roles",
            "saml.force.post.binding");

    private final RealmKeycloakMapper realmMapper;
    private final ClientKeycloakMapper clientMapper;
    private final ClientPolicyKeycloakMapper clientPolicyMapper;
    private final ClientScopeKeycloakMapper clientScopeMapper;
    private final IdentityProviderKeycloakMapper identityProviderMapper;
    private final AuthenticationFlowKeycloakMapper authenticationFlowMapper;
    private final ComponentKeycloakMapper componentMapper;
    private final GroupKeycloakMapper groupMapper;
    private final RequiredActionKeycloakMapper requiredActionMapper;
    private final RoleKeycloakMapper roleMapper;
    private final UnmodeledAttributesConverter unmodeledAttributes;

    @SuppressWarnings("java:S107") // One mapper per type of resource
    RealmExportReader(
            final RealmKeycloakMapper realmMapper,
            final ClientKeycloakMapper clientMapper,
            final ClientPolicyKeycloakMapper clientPolicyMapper,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final IdentityProviderKeycloakMapper identityProviderMapper,
            final AuthenticationFlowKeycloakMapper authenticationFlowMapper,
            final ComponentKeycloakMapper componentMapper,
            final GroupKeycloakMapper groupMapper,
            final RequiredActionKeycloakMapper requiredActionMapper,
            final RoleKeycloakMapper roleMapper,
            final UnmodeledAttributesConverter unmodeledAttributes) {
        this.realmMapper = realmMapper;
        this.clientMapper = clientMapper;
        this.clientPolicyMapper = clientPolicyMapper;
        this.clientScopeMapper = clientScopeMapper;
        this.identityProviderMapper = identityProviderMapper;
        this.authenticationFlowMapper = authenticationFlowMapper;
        this.componentMapper = componentMapper;
        this.groupMapper = groupMapper;
        this.requiredActionMapper = requiredActionMapper;
        this.roleMapper = roleMapper;
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /** The resources of a realm; the given representation is left as it is. */
    DesiredState read(final RealmRepresentation exported) {
        RealmRepresentation realm = CloneUtil.deepClone(exported, RealmRepresentation.class);
        DesiredState state = new DesiredState();
        state.declare(ResourceType.REALM_SETTINGS, List.of(settingsOf(realm)));
        declareRealmDefaults(realm, state);
        declareClients(realm, state);
        declareClientPolicies(realm, state);
        declareScopeMappings(realm, state);
        if (realm.getIdentityProviders() != null) {
            state.declare(
                    ResourceType.IDENTITY_PROVIDER,
                    realm.getIdentityProviders().stream()
                            .map(this.identityProviderMapper::toIdentityProvider)
                            .toList());
        }
        if (realm.getIdentityProviderMappers() != null) {
            state.declare(
                    ResourceType.IDENTITY_PROVIDER_MAPPER,
                    realm.getIdentityProviderMappers().stream()
                            .map(this.identityProviderMapper::toMapper)
                            .toList());
        }
        declareFlows(realm, state);
        declareComponents(realm, state);
        if (realm.getGroups() != null) {
            List<Group> groups = new ArrayList<>();
            flattenGroups(realm.getGroups(), null, groups);
            state.declare(ResourceType.GROUP, groups);
        }
        if (realm.getClientScopes() != null) {
            state.declare(
                    ResourceType.CLIENT_SCOPE,
                    realm.getClientScopes().stream()
                            .map(scope -> this.clientScopeMapper.toClientScope(scope, false))
                            .toList());
        }
        if (realm.getRequiredActions() != null) {
            state.declare(
                    ResourceType.REQUIRED_ACTION,
                    realm.getRequiredActions().stream()
                            .map(this.requiredActionMapper::toRequiredAction)
                            .toList());
        }
        declareRoles(realm.getRoles(), state);
        return state;
    }

    private RealmSettings settingsOf(final RealmRepresentation realm) {
        RealmSettings settings = this.realmMapper.toSettings(realm);
        Map<String, Object> values = new LinkedHashMap<>(settings.settings().values());
        values.keySet().removeAll(IGNORED_SETTINGS);
        return new RealmSettings(settings.realm(), new UnmodeledAttributes(values));
    }

    private static void declareRealmDefaults(final RealmRepresentation realm, final DesiredState state) {
        List<DefaultClientScopes> defaultClientScopes = new ArrayList<>();
        if (realm.getDefaultDefaultClientScopes() != null) {
            defaultClientScopes.add(new DefaultClientScopes(
                    DefaultClientScopes.Kind.DEFAULT, Set.copyOf(realm.getDefaultDefaultClientScopes())));
        }
        if (realm.getDefaultOptionalClientScopes() != null) {
            defaultClientScopes.add(new DefaultClientScopes(
                    DefaultClientScopes.Kind.OPTIONAL, Set.copyOf(realm.getDefaultOptionalClientScopes())));
        }
        state.declare(ResourceType.DEFAULT_CLIENT_SCOPE, defaultClientScopes);
        state.declare(ResourceType.REALM_FLOW_BINDING, List.of(RealmKeycloakGateway.flowBindingsOf(realm)));
        if (realm.getDefaultGroups() != null) {
            state.declare(ResourceType.DEFAULT_GROUP, List.of(new DefaultGroups(Set.copyOf(realm.getDefaultGroups()))));
        }
    }

    private void declareClients(final RealmRepresentation realm, final DesiredState state) {
        if (realm.getClients() == null) {
            return;
        }
        Map<String, String> flowAliases = new HashMap<>();
        if (realm.getAuthenticationFlows() != null) {
            realm.getAuthenticationFlows().forEach(flow -> flowAliases.put(flow.getId(), flow.getAlias()));
        }
        List<Client> clients = new ArrayList<>();
        List<ClientFlowBindings> bindings = new ArrayList<>();
        List<ClientAuthorization> authorizations = new ArrayList<>();
        for (ClientRepresentation client : realm.getClients()) {
            if (client.getClientId() == null && client.getName() == null) {
                continue;
            }
            withoutSamlAttributes(client);
            clients.add(this.clientMapper.toClient(client));
            Map<String, String> overrides = client.getAuthenticationFlowBindingOverrides();
            if (overrides != null && !overrides.isEmpty()) {
                Map<String, String> byAlias = new LinkedHashMap<>();
                overrides.forEach((binding, flowId) -> byAlias.put(binding, flowAliases.getOrDefault(flowId, flowId)));
                bindings.add(new ClientFlowBindings(client.getClientId(), client.getName(), byAlias));
            }
            if (client.getAuthorizationSettings() != null) {
                authorizations.add(new ClientAuthorization(
                        client.getClientId(),
                        client.getName(),
                        this.unmodeledAttributes.extract(client.getAuthorizationSettings(), Set.of())));
            }
        }
        state.declare(ResourceType.CLIENT, clients);
        state.declare(ResourceType.CLIENT_FLOW_BINDING, bindings);
        state.declare(ResourceType.CLIENT_AUTHORIZATION, authorizations);
    }

    /** Older versions of Keycloak give SAML attributes to OpenID Connect clients, the ones without protocol included. */
    private static void withoutSamlAttributes(final ClientRepresentation client) {
        String protocol = client.getProtocol();
        if ((protocol == null || "openid-connect".equals(protocol)) && client.getAttributes() != null) {
            Map<String, String> attributes = new LinkedHashMap<>(client.getAttributes());
            attributes.keySet().removeAll(SAML_ATTRIBUTES);
            client.setAttributes(attributes);
        }
    }

    private void declareClientPolicies(final RealmRepresentation realm, final DesiredState state) {
        ClientProfilesRepresentation profiles = realm.getParsedClientProfiles();
        if (profiles != null && profiles.getProfiles() != null) {
            state.declare(
                    ResourceType.CLIENT_PROFILE,
                    profiles.getProfiles().stream()
                            .map(this.clientPolicyMapper::toProfile)
                            .toList());
        }
        ClientPoliciesRepresentation policies = realm.getParsedClientPolicies();
        if (policies != null && policies.getPolicies() != null) {
            state.declare(
                    ResourceType.CLIENT_POLICY,
                    policies.getPolicies().stream()
                            .map(this.clientPolicyMapper::toPolicy)
                            .toList());
        }
    }

    private static void declareScopeMappings(final RealmRepresentation realm, final DesiredState state) {
        if (realm.getScopeMappings() != null) {
            state.declare(
                    ResourceType.SCOPE_MAPPING,
                    realm.getScopeMappings().stream()
                            .map(mapping -> scopeMappingOf(mapping, null))
                            .flatMap(Optional::stream)
                            .toList());
        }
        if (realm.getClientScopeMappings() != null) {
            List<ScopeMapping> mappings = new ArrayList<>();
            realm.getClientScopeMappings().forEach((roleClientId, declared) -> {
                if (declared != null) {
                    declared.forEach(
                            mapping -> scopeMappingOf(mapping, roleClientId).ifPresent(mappings::add));
                }
            });
            state.declare(ResourceType.CLIENT_SCOPE_MAPPING, mappings);
        }
    }

    private static Optional<ScopeMapping> scopeMappingOf(
            final ScopeMappingRepresentation mapping, final String roleClientId) {
        if (mapping.getClient() != null) {
            return Optional.of(
                    new ScopeMapping(ScopeMapping.Key.ofClient(mapping.getClient(), roleClientId), mapping.getRoles()));
        }
        if (mapping.getClientScope() != null) {
            return Optional.of(new ScopeMapping(
                    ScopeMapping.Key.ofClientScope(mapping.getClientScope(), roleClientId), mapping.getRoles()));
        }
        return Optional.empty();
    }

    /**
     * Each built-in flow, and each other top-level flow with its tree and the authenticator configs it uses, without
     * their ids. Other flows are used by none of them: they are left out.
     */
    @SuppressWarnings("deprecation")
    private void declareFlows(final RealmRepresentation realm, final DesiredState state) {
        List<AuthenticationFlowRepresentation> representations = realm.getAuthenticationFlows();
        List<AuthenticatorConfigRepresentation> configRepresentations = realm.getAuthenticatorConfig();
        if (representations == null && configRepresentations == null) {
            return;
        }
        Map<String, AuthenticationFlowRepresentation> byAlias = new LinkedHashMap<>();
        if (representations != null) {
            representations.forEach(flow -> byAlias.putIfAbsent(flow.getAlias(), flow));
        }
        Map<String, AuthenticatorConfig> configs = new LinkedHashMap<>();
        if (configRepresentations != null) {
            configRepresentations.forEach(config -> configs.putIfAbsent(
                    config.getAlias(), this.authenticationFlowMapper.toAuthenticatorConfig(config, false)));
        }
        List<AuthenticationFlow> flows = new ArrayList<>();
        Set<String> inATree = new LinkedHashSet<>();
        Set<String> usedConfigs = new LinkedHashSet<>();
        for (AuthenticationFlow flow :
                this.authenticationFlowMapper.toCurrentFlows(representations == null ? List.of() : representations)) {
            Set<String> tree = new LinkedHashSet<>();
            tree.add(flow.alias());
            tree.addAll(flow.subFlows().keySet());
            inATree.addAll(tree);
            Map<String, AuthenticatorConfig> used = new LinkedHashMap<>();
            tree.stream()
                    .map(byAlias::get)
                    .filter(Objects::nonNull)
                    .flatMap(representation -> representation.getAuthenticationExecutions() == null
                            ? java.util.stream.Stream.<AuthenticationExecutionExportRepresentation>empty()
                            : representation.getAuthenticationExecutions().stream())
                    .map(AuthenticationExecutionExportRepresentation::getAuthenticatorConfig)
                    .filter(configs::containsKey)
                    .forEach(alias -> used.put(alias, configs.get(alias)));
            usedConfigs.addAll(used.keySet());
            flows.add(new AuthenticationFlow(
                    flow.alias(), null, flow.builtIn(), flow.definition(), flow.subFlows(), used));
        }
        warnAboutInvalidExecutions(realm.getRealm(), byAlias);
        Set<String> unusedFlows = byAlias.keySet().stream()
                .filter(alias -> !inATree.contains(alias))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unusedFlows.isEmpty()) {
            logger.warn(
                    "The following authentication flows of realm '{}' are unused and left out: {}",
                    realm.getRealm(),
                    unusedFlows);
        }
        Set<String> unusedConfigs = configs.keySet().stream()
                .filter(alias -> !usedConfigs.contains(alias))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unusedConfigs.isEmpty()) {
            logger.warn("Some authenticator configs of realm '{}' are unused: {}", realm.getRealm(), unusedConfigs);
        }
        state.declare(ResourceType.AUTHENTICATION_FLOW, flows);
        state.declare(ResourceType.AUTHENTICATOR_CONFIG, List.copyOf(configs.values()));
    }

    /** An execution can only set an authenticator on a form sub-flow: an import refuses the others. */
    private static void warnAboutInvalidExecutions(
            final String realm, final Map<String, AuthenticationFlowRepresentation> flows) {
        flows.values().forEach(flow -> {
            if (flow.getAuthenticationExecutions() == null) {
                return;
            }
            flow.getAuthenticationExecutions().forEach(execution -> {
                AuthenticationFlowRepresentation subFlow =
                        execution.getFlowAlias() == null ? null : flows.get(execution.getFlowAlias());
                if (execution.getAuthenticator() != null
                        && subFlow != null
                        && !"form-flow".equals(subFlow.getProviderId())) {
                    logger.error(
                            "An execution of flow '{}' of realm '{}' defines an authenticator and references the"
                                    + " sub-flow '{}', of type '{}': only a sub-flow of type 'form-flow' allows it."
                                    + " keycloak-config-cli will refuse to import this flow.",
                            flow.getAlias(),
                            realm,
                            subFlow.getAlias(),
                            subFlow.getProviderId());
                }
            });
        });
    }

    private void declareComponents(final RealmRepresentation realm, final DesiredState state) {
        if (realm.getComponents() == null) {
            return;
        }
        Map<String, List<ComponentExportRepresentation>> components = new LinkedHashMap<>(realm.getComponents());
        components.remove(KEY_PROVIDER);
        if (components.remove(USER_PROFILE_PROVIDER) != null) {
            logger.info("The user profile of realm '{}' is not normalized: it is left out", realm.getRealm());
        }
        List<Component> flattened = this.componentMapper.flatten(components).stream()
                .map(component -> new Component(component.key(), null, component.providerId(), component.config()))
                .toList();
        state.declare(
                ResourceType.COMPONENT,
                flattened.stream()
                        .filter(component -> component.key().isTopLevel())
                        .toList());
        state.declare(
                ResourceType.SUB_COMPONENT,
                flattened.stream()
                        .filter(component -> !component.key().isTopLevel())
                        .toList());
    }

    private void flattenGroups(
            final List<GroupRepresentation> declared, final String parentPath, final List<Group> groups) {
        for (GroupRepresentation group : declared) {
            String path = Group.childPath(parentPath, group.getName());
            Set<String> children = group.getSubGroups() == null
                    ? null
                    : group.getSubGroups().stream()
                            .map(GroupRepresentation::getName)
                            .collect(Collectors.toSet());
            groups.add(this.groupMapper.toGroup(group, path, children));
            if (group.getSubGroups() != null) {
                flattenGroups(group.getSubGroups(), path, groups);
            }
        }
    }

    private void declareRoles(final RolesRepresentation roles, final DesiredState state) {
        if (roles == null) {
            return;
        }
        List<RoleComposites> composites = new ArrayList<>();
        if (roles.getRealm() != null) {
            state.declare(
                    ResourceType.REALM_ROLE,
                    roles.getRealm().stream()
                            .map(role -> this.roleMapper.toRole(role, null))
                            .toList());
            roles.getRealm()
                    .forEach(role -> this.roleMapper.toComposites(role, null).ifPresent(composites::add));
        }
        if (roles.getClient() != null) {
            List<Role> clientRoles = new ArrayList<>();
            roles.getClient().forEach((clientId, declared) -> {
                if (declared != null) {
                    declared.forEach(role -> {
                        clientRoles.add(this.roleMapper.toRole(role, clientId));
                        this.roleMapper.toComposites(role, clientId).ifPresent(composites::add);
                    });
                }
            });
            state.declare(ResourceType.CLIENT_ROLE, clientRoles);
        }
        state.declare(ResourceType.ROLE_COMPOSITE, composites);
    }
}
