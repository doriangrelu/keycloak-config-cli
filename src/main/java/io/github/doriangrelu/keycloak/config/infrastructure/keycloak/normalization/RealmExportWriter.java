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
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientPolicy;
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientProfile;
import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ClientScope;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProvider;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProviderMapper;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.authenticationflow.AuthenticationFlowKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy.ClientPolicyKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group.GroupKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider.IdentityProviderKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm.RealmKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction.RequiredActionKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role.RoleKeycloakMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientPoliciesRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;

/** Writes the resources of a normalized realm as an import file. Lists are sorted, so that the file is stable. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "run",
        name = "operation",
        havingValue = "NORMALIZE")
class RealmExportWriter {

    private final RealmKeycloakMapper realmMapper;
    private final ClientKeycloakMapper clientMapper;
    private final ClientPolicyKeycloakMapper clientPolicyMapper;
    private final ClientScopeKeycloakMapper clientScopeMapper;
    private final IdentityProviderKeycloakMapper identityProviderMapper;
    private final AuthenticationFlowKeycloakMapper authenticationFlowMapper;
    private final GroupKeycloakMapper groupMapper;
    private final RequiredActionKeycloakMapper requiredActionMapper;
    private final RoleKeycloakMapper roleMapper;
    private final UnmodeledAttributesConverter unmodeledAttributes;

    @SuppressWarnings("java:S107") // One mapper per type of resource
    RealmExportWriter(
            final RealmKeycloakMapper realmMapper,
            final ClientKeycloakMapper clientMapper,
            final ClientPolicyKeycloakMapper clientPolicyMapper,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final IdentityProviderKeycloakMapper identityProviderMapper,
            final AuthenticationFlowKeycloakMapper authenticationFlowMapper,
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
        this.groupMapper = groupMapper;
        this.requiredActionMapper = requiredActionMapper;
        this.roleMapper = roleMapper;
        this.unmodeledAttributes = unmodeledAttributes;
    }

    RealmRepresentation write(final String realmName, final DesiredState normalized) {
        RealmRepresentation realm = this.<RealmSettings>of(normalized, ResourceType.REALM_SETTINGS).stream()
                .findFirst()
                .map(this.realmMapper::toRepresentation)
                .orElseGet(RealmRepresentation::new);
        realm.setRealm(realmName);
        this.<RealmFlowBindings>of(normalized, ResourceType.REALM_FLOW_BINDING)
                .forEach(bindings -> this.unmodeledAttributes.apply(
                        new UnmodeledAttributes(new LinkedHashMap<>(bindings.flows())), realm));
        this.<DefaultClientScopes>of(normalized, ResourceType.DEFAULT_CLIENT_SCOPE)
                .forEach(scopes -> {
                    List<String> sorted = scopes.scopes().stream().sorted().toList();
                    if (scopes.kind() == DefaultClientScopes.Kind.DEFAULT) {
                        realm.setDefaultDefaultClientScopes(sorted);
                    } else {
                        realm.setDefaultOptionalClientScopes(sorted);
                    }
                });
        this.<DefaultGroups>of(normalized, ResourceType.DEFAULT_GROUP)
                .forEach(groups ->
                        realm.setDefaultGroups(groups.paths().stream().sorted().toList()));
        writeClients(normalized, realm);
        writeClientPolicies(normalized, realm);
        writeScopeMappings(normalized, realm);
        realm.setIdentityProviders(list(
                normalized,
                ResourceType.IDENTITY_PROVIDER,
                (IdentityProvider provider) -> this.identityProviderMapper.toRepresentation(provider)));
        realm.setIdentityProviderMappers(list(
                normalized,
                ResourceType.IDENTITY_PROVIDER_MAPPER,
                (IdentityProviderMapper mapper) -> this.identityProviderMapper.toRepresentation(mapper)));
        writeFlows(normalized, realm);
        writeComponents(normalized, realm);
        writeGroups(normalized, realm);
        realm.setClientScopes(list(
                normalized,
                ResourceType.CLIENT_SCOPE,
                (ClientScope scope) -> this.clientScopeMapper.toRepresentation(scope)));
        realm.setRequiredActions(list(
                normalized,
                ResourceType.REQUIRED_ACTION,
                (RequiredAction action) -> this.requiredActionMapper.toRepresentation(action)));
        writeRoles(normalized, realm);
        return realm;
    }

    private void writeClients(final DesiredState normalized, final RealmRepresentation realm) {
        Map<String, ClientFlowBindings> bindings = byKey(normalized, ResourceType.CLIENT_FLOW_BINDING);
        Map<String, ClientAuthorization> authorizations = byKey(normalized, ResourceType.CLIENT_AUTHORIZATION);
        realm.setClients(list(normalized, ResourceType.CLIENT, (Client client) -> {
            ClientRepresentation representation = this.clientMapper.toRepresentation(client);
            ClientFlowBindings clientBindings = bindings.get(client.key());
            if (clientBindings != null) {
                representation.setAuthenticationFlowBindingOverrides(new LinkedHashMap<>(clientBindings.overrides()));
            }
            ClientAuthorization authorization = authorizations.get(client.key());
            if (authorization != null) {
                ResourceServerRepresentation settings = new ResourceServerRepresentation();
                this.unmodeledAttributes.apply(authorization.settings(), settings);
                representation.setAuthorizationSettings(settings);
            }
            return representation;
        }));
    }

    private void writeClientPolicies(final DesiredState normalized, final RealmRepresentation realm) {
        List<ClientProfile> profiles = of(normalized, ResourceType.CLIENT_PROFILE);
        if (!profiles.isEmpty()) {
            ClientProfilesRepresentation representation = new ClientProfilesRepresentation();
            representation.setProfiles(profiles.stream()
                    .map(this.clientPolicyMapper::toRepresentation)
                    .toList());
            realm.setParsedClientProfiles(representation);
        }
        List<ClientPolicy> policies = of(normalized, ResourceType.CLIENT_POLICY);
        if (!policies.isEmpty()) {
            ClientPoliciesRepresentation representation = new ClientPoliciesRepresentation();
            representation.setPolicies(policies.stream()
                    .map(this.clientPolicyMapper::toRepresentation)
                    .toList());
            realm.setParsedClientPolicies(representation);
        }
    }

    private void writeScopeMappings(final DesiredState normalized, final RealmRepresentation realm) {
        this.<ScopeMapping>of(normalized, ResourceType.SCOPE_MAPPING).forEach(mapping -> {
            ScopeMappingRepresentation representation = mapping.key().holderType() == ScopeMapping.HolderType.CLIENT
                    ? realm.clientScopeMapping(mapping.key().holder())
                    : realm.clientScopeScopeMapping(mapping.key().holder());
            representation.setRoles(new java.util.LinkedHashSet<>(
                    mapping.roles().stream().sorted().toList()));
        });
        List<ScopeMapping> clientScopeMappings = of(normalized, ResourceType.CLIENT_SCOPE_MAPPING);
        if (!clientScopeMappings.isEmpty()) {
            Map<String, List<ScopeMappingRepresentation>> byClient = new LinkedHashMap<>();
            clientScopeMappings.forEach(mapping -> {
                ScopeMappingRepresentation representation = new ScopeMappingRepresentation();
                if (mapping.key().holderType() == ScopeMapping.HolderType.CLIENT) {
                    representation.setClient(mapping.key().holder());
                } else {
                    representation.setClientScope(mapping.key().holder());
                }
                representation.setRoles(new java.util.LinkedHashSet<>(
                        mapping.roles().stream().sorted().toList()));
                byClient.computeIfAbsent(mapping.key().roleClientId(), clientId -> new ArrayList<>())
                        .add(representation);
            });
            realm.setClientScopeMappings(byClient);
        }
    }

    /** Each top-level flow with its sub-flows, and the authenticator configs. */
    private void writeFlows(final DesiredState normalized, final RealmRepresentation realm) {
        List<AuthenticationFlow> flows = of(normalized, ResourceType.AUTHENTICATION_FLOW);
        if (!flows.isEmpty()) {
            Map<String, AuthenticationFlowRepresentation> written = new LinkedHashMap<>();
            for (AuthenticationFlow flow : flows) {
                written.put(flow.alias(), this.authenticationFlowMapper.toRepresentation(flow));
                this.authenticationFlowMapper.subFlowsOf(flow).forEach(written::putIfAbsent);
            }
            realm.setAuthenticationFlows(new ArrayList<>(written.values()));
        }
        realm.setAuthenticatorConfig(list(
                normalized,
                ResourceType.AUTHENTICATOR_CONFIG,
                (AuthenticatorConfig config) -> this.authenticationFlowMapper.toRepresentation(config, null)));
    }

    private static void writeComponents(final DesiredState normalized, final RealmRepresentation realm) {
        List<Component> components = new ArrayList<>(of(normalized, ResourceType.COMPONENT));
        components.addAll(of(normalized, ResourceType.SUB_COMPONENT));
        if (components.isEmpty()) {
            return;
        }
        Map<Component.Key, ComponentExportRepresentation> byKey = new LinkedHashMap<>();
        MultivaluedHashMap<String, ComponentExportRepresentation> topLevel = new MultivaluedHashMap<>();
        for (Component component : components) {
            ComponentExportRepresentation representation = new ComponentExportRepresentation();
            representation.setName(component.key().name());
            representation.setProviderId(component.providerId());
            representation.setSubType(component.key().subType());
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            component.config().forEach((name, values) -> config.put(name, new ArrayList<>(values)));
            representation.setConfig(config);
            representation.setSubComponents(new MultivaluedHashMap<>());
            byKey.put(component.key(), representation);
        }
        for (Component component : components) {
            ComponentExportRepresentation representation = byKey.get(component.key());
            if (component.key().isTopLevel()) {
                topLevel.add(component.key().providerType(), representation);
            } else {
                ComponentExportRepresentation parent = byKey.get(component.key().parent());
                if (parent != null) {
                    parent.getSubComponents().add(component.key().providerType(), representation);
                }
            }
        }
        realm.setComponents(topLevel);
    }

    /** Groups nested in their parent group. */
    private void writeGroups(final DesiredState normalized, final RealmRepresentation realm) {
        List<Group> groups = of(normalized, ResourceType.GROUP);
        if (groups.isEmpty()) {
            return;
        }
        Map<String, GroupRepresentation> byPath = new LinkedHashMap<>();
        List<GroupRepresentation> topLevel = new ArrayList<>();
        for (Group group : groups) {
            GroupRepresentation representation = this.groupMapper.toRepresentation(group);
            representation.setPath(group.path());
            representation.setRealmRoles(
                    group.realmRoles() == null
                            ? null
                            : group.realmRoles().stream().sorted().toList());
            if (group.clientRoles() != null) {
                Map<String, List<String>> clientRoles = new LinkedHashMap<>();
                group.clientRoles()
                        .forEach((clientId, roles) -> clientRoles.put(
                                clientId, roles.stream().sorted().toList()));
                representation.setClientRoles(clientRoles);
            }
            representation.setSubGroups(new ArrayList<>());
            withoutEmptyValues(representation);
            byPath.put(group.path(), representation);
            String parentPath = group.path().substring(0, group.path().lastIndexOf('/'));
            GroupRepresentation parent = byPath.get(parentPath);
            if (parent == null) {
                topLevel.add(representation);
            } else {
                parent.getSubGroups().add(representation);
            }
        }
        realm.setGroups(topLevel);
    }

    /** Empty attributes and roles of a group are left out, as in an export of Keycloak. */
    private static void withoutEmptyValues(final GroupRepresentation group) {
        if (group.getAttributes() != null && group.getAttributes().isEmpty()) {
            group.setAttributes(null);
        }
        if (group.getRealmRoles() != null && group.getRealmRoles().isEmpty()) {
            group.setRealmRoles(null);
        }
        if (group.getClientRoles() != null && group.getClientRoles().isEmpty()) {
            group.setClientRoles(null);
        }
    }

    private void writeRoles(final DesiredState normalized, final RealmRepresentation realm) {
        List<Role> realmRoles = of(normalized, ResourceType.REALM_ROLE);
        List<Role> clientRoles = of(normalized, ResourceType.CLIENT_ROLE);
        if (realmRoles.isEmpty() && clientRoles.isEmpty()) {
            return;
        }
        Map<RoleKey, RoleComposites> composites = byKey(normalized, ResourceType.ROLE_COMPOSITE);
        Function<Role, RoleRepresentation> toRepresentation = role -> {
            RoleRepresentation representation = this.roleMapper.toRepresentation(role);
            if (representation.getAttributes() != null
                    && representation.getAttributes().isEmpty()) {
                representation.setAttributes(null);
            }
            RoleComposites roleComposites = composites.get(role.key());
            if (roleComposites != null && !roleComposites.roles().isEmpty()) {
                RoleRepresentation.Composites written = new RoleRepresentation.Composites();
                if (roleComposites.realm() != null && !roleComposites.realm().isEmpty()) {
                    written.setRealm(new java.util.LinkedHashSet<>(
                            roleComposites.realm().stream().sorted().toList()));
                }
                if (roleComposites.client() != null && !roleComposites.client().isEmpty()) {
                    Map<String, List<String>> client = new LinkedHashMap<>();
                    roleComposites
                            .client()
                            .forEach((clientId, names) ->
                                    client.put(clientId, names.stream().sorted().toList()));
                    written.setClient(client);
                }
                representation.setComposite(true);
                representation.setComposites(written);
            }
            return representation;
        };
        RolesRepresentation roles = new RolesRepresentation();
        if (!realmRoles.isEmpty()) {
            roles.setRealm(realmRoles.stream().map(toRepresentation).collect(Collectors.toList()));
        }
        if (!clientRoles.isEmpty()) {
            Map<String, List<RoleRepresentation>> byClient = new LinkedHashMap<>();
            clientRoles.forEach(role -> byClient.computeIfAbsent(role.key().clientId(), clientId -> new ArrayList<>())
                    .add(toRepresentation.apply(role)));
            roles.setClient(byClient);
        }
        realm.setRoles(roles);
    }

    private static <R, T> List<T> list(
            final DesiredState normalized, final ResourceType type, final Function<R, T> toRepresentation) {
        List<R> resources = of(normalized, type);
        return resources.isEmpty()
                ? null
                : resources.stream().map(toRepresentation).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static <R> List<R> of(final DesiredState state, final ResourceType type) {
        return state.resources(type).map(resources -> (List<R>) resources).orElse(List.of());
    }

    private static <K, R extends io.github.doriangrelu.keycloak.config.domain.reconcile.Resource<K>> Map<K, R> byKey(
            final DesiredState state, final ResourceType type) {
        List<R> resources = of(state, type);
        return resources.stream()
                .collect(Collectors.toMap(
                        io.github.doriangrelu.keycloak.config.domain.reconcile.Resource::key,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }
}
