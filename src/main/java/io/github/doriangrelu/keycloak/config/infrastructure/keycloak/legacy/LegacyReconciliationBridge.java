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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.legacy;

import io.github.doriangrelu.keycloak.config.application.client.ClientReconciler;
import io.github.doriangrelu.keycloak.config.application.component.ComponentReconciler;
import io.github.doriangrelu.keycloak.config.application.messagebundle.MessageBundleReconciler;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.requiredaction.RequiredActionReconciler;
import io.github.doriangrelu.keycloak.config.application.role.ClientRoleReconciler;
import io.github.doriangrelu.keycloak.config.application.role.RealmRoleReconciler;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.common.ManagedMode;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import io.github.doriangrelu.keycloak.config.domain.user.model.InvalidUserException;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.domain.userprofile.model.UserProfile;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy.ClientPolicyKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.component.ComponentKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group.GroupKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider.IdentityProviderKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction.RequiredActionKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role.RoleKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.user.UserKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.userprofile.UserProfileKeycloakMapper;
import io.github.doriangrelu.keycloak.config.model.ProtectedResource;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues;
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import io.github.doriangrelu.keycloak.config.service.state.StateService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.ClientPoliciesRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Transitional glue between the legacy import, which works file by file on {@link RealmImport}, and the
 * reconciliation engine, for the resource types already migrated (EPIC-002). Removed when the import runs on the
 * engine only (EPIC-003).
 *
 * <p>The {@link RealmContext} of each realm is kept in the legacy execution context of the run: only this class
 * reads it, and passes it explicitly to the engine.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class LegacyReconciliationBridge {

    private static final Logger logger = LoggerFactory.getLogger(LegacyReconciliationBridge.class);

    /** Realm attribute that enables the management of the user profile configuration. */
    static final String USER_PROFILE_ENABLED = "userProfileEnabled";

    private final ReconciliationEngine engine;
    private final RoleKeycloakMapper roleMapper;
    private final ClientKeycloakMapper clientMapper;
    private final GroupKeycloakMapper groupMapper;
    private final UserKeycloakMapper userMapper;
    private final IdentityProviderKeycloakMapper identityProviderMapper;
    private final ClientPolicyKeycloakMapper clientPolicyMapper;
    private final ComponentKeycloakMapper componentMapper;
    private final UserProfileKeycloakMapper userProfileMapper;
    private final ClientApi clientApi;
    private final KeycloakProvider keycloakProvider;
    private final ClientScopeKeycloakMapper clientScopeMapper;
    private final RequiredActionKeycloakMapper requiredActionMapper;
    private final StateService stateService;
    private final ImportConfigProperties properties;

    public LegacyReconciliationBridge(
            final ReconciliationEngine engine,
            final RoleKeycloakMapper roleMapper,
            final ClientKeycloakMapper clientMapper,
            final GroupKeycloakMapper groupMapper,
            final UserKeycloakMapper userMapper,
            final IdentityProviderKeycloakMapper identityProviderMapper,
            final ClientPolicyKeycloakMapper clientPolicyMapper,
            final ComponentKeycloakMapper componentMapper,
            final UserProfileKeycloakMapper userProfileMapper,
            final ClientApi clientApi,
            final KeycloakProvider keycloakProvider,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final RequiredActionKeycloakMapper requiredActionMapper,
            final StateService stateService,
            final ImportConfigProperties properties) {
        this.engine = engine;
        this.roleMapper = roleMapper;
        this.clientMapper = clientMapper;
        this.groupMapper = groupMapper;
        this.userMapper = userMapper;
        this.identityProviderMapper = identityProviderMapper;
        this.clientPolicyMapper = clientPolicyMapper;
        this.componentMapper = componentMapper;
        this.userProfileMapper = userProfileMapper;
        this.clientApi = clientApi;
        this.keycloakProvider = keycloakProvider;
        this.clientScopeMapper = clientScopeMapper;
        this.requiredActionMapper = requiredActionMapper;
        this.stateService = stateService;
        this.properties = properties;
    }

    /**
     * Prepares the import of one file: captures the remote state before the import overwrites it, and adds what the
     * file declares to the desired state of its realm. Called for every file, including those whose import is
     * skipped because their checksum did not change.
     *
     * @param realmExists whether the realm already exists in Keycloak
     * @param remoteStateLoaded whether the remote state of the realm was loaded for this file
     */
    public void startFile(final RealmImport realmImport, final boolean realmExists, final boolean remoteStateLoaded) {
        RealmContext context = contextOf(realmImport);
        context.addProtections(protectionsOf(realmImport));
        if (remoteStateLoaded) {
            if (realmImport.getClients() != null) {
                context.captureState(ClientReconciler.STATE_SCOPE, this.stateService.getClients());
            }
            captureRoleState(realmImport, context);
            if (realmImport.getRequiredActions() != null) {
                context.captureState(RequiredActionReconciler.STATE_SCOPE, this.stateService.getRequiredActions());
            }
            if (realmImport.getMessageBundles() != null) {
                context.captureState(MessageBundleReconciler.STATE_SCOPE, this.stateService.getMessageBundles());
            }
            captureComponentState(realmImport, context);
        }
        this.engine.register(context, desiredStateOf(realmImport, realmExists));
    }

    /**
     * Creates and updates what one file declares for some of the migrated types. The legacy import calls it at the
     * place each type had, so that the types it still handles find what they rely on.
     */
    public void apply(final RealmImport realmImport, final Set<ResourceType> types) {
        this.engine.apply(contextOf(realmImport), desiredStateOf(realmImport, true), types);
    }

    /** Deletes, once per realm, the managed resources of the migrated types missing from every file of the realm. */
    public void prune(final RealmImport realmImport) {
        this.engine.prune(contextOf(realmImport));
    }

    private synchronized RealmContext contextOf(final RealmImport realmImport) {
        String realm = realmImport.getRealm();
        return ExecutionContextHolder.context().get(realm, RealmContext.class).stream()
                .findFirst()
                .orElseGet(() -> {
                    RealmContext created = new RealmContext(
                            realm,
                            this.properties.isParallel(),
                            this.properties.getRemoteState().isEnabled(),
                            Map.ofEntries(
                                    Map.entry(ResourceType.REALM_ROLE, roleMode()),
                                    Map.entry(ResourceType.CLIENT_ROLE, roleMode()),
                                    Map.entry(ResourceType.REQUIRED_ACTION, requiredActionMode()),
                                    Map.entry(ResourceType.MESSAGE_BUNDLE, messageBundleMode()),
                                    Map.entry(ResourceType.CLIENT_SCOPE, clientScopeMode()),
                                    Map.entry(ResourceType.CLIENT, clientMode()),
                                    Map.entry(ResourceType.GROUP, groupMode()),
                                    Map.entry(
                                            ResourceType.IDENTITY_PROVIDER,
                                            modeOf(this.properties.getManaged().getIdentityProvider())),
                                    Map.entry(
                                            ResourceType.IDENTITY_PROVIDER_MAPPER,
                                            modeOf(this.properties.getManaged().getIdentityProviderMapper())),
                                    // The declared client profiles and policies are the exact ones, as before
                                    Map.entry(ResourceType.CLIENT_PROFILE, ManagedMode.FULL),
                                    Map.entry(ResourceType.CLIENT_POLICY, ManagedMode.FULL),
                                    Map.entry(
                                            ResourceType.SCOPE_MAPPING,
                                            modeOf(this.properties.getManaged().getScopeMapping())),
                                    Map.entry(
                                            ResourceType.CLIENT_SCOPE_MAPPING,
                                            modeOf(this.properties.getManaged().getClientScopeMapping())),
                                    Map.entry(
                                            ResourceType.COMPONENT,
                                            modeOf(this.properties.getManaged().getComponent())),
                                    Map.entry(
                                            ResourceType.SUB_COMPONENT,
                                            modeOf(this.properties.getManaged().getSubComponent()))));
                    ExecutionContextHolder.context().put(realm, RealmContext.class, List.of(created));
                    return created;
                });
    }

    private static ManagedMode modeOf(final ImportManagedPropertiesValues value) {
        return value == ImportManagedPropertiesValues.FULL ? ManagedMode.FULL : ManagedMode.NO_DELETE;
    }

    private ManagedMode groupMode() {
        return this.properties.getManaged().getGroup() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode clientMode() {
        return this.properties.getManaged().getClient() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode clientScopeMode() {
        return this.properties.getManaged().getClientScope() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode messageBundleMode() {
        return this.properties.getManaged().getMessageBundles() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode requiredActionMode() {
        return this.properties.getManaged().getRequiredAction() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode roleMode() {
        return this.properties.getManaged().getRole() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private void captureComponentState(final RealmImport realmImport, final RealmContext context) {
        if (realmImport.getComponents() == null) {
            return;
        }
        context.captureState("components", this.stateService.getComponents(null));
        this.componentMapper.flatten(realmImport.getComponents()).forEach(component -> {
            String name = component.key().name();
            context.captureState(
                    ComponentReconciler.subComponentStateScope(name), this.stateService.getComponents(name));
        });
    }

    private void captureRoleState(final RealmImport realmImport, final RealmContext context) {
        RolesRepresentation roles = realmImport.getRoles();
        if (roles == null) {
            return;
        }
        if (roles.getRealm() != null) {
            context.captureState(RealmRoleReconciler.STATE_SCOPE, this.stateService.getRealmRoles());
        }
        if (roles.getClient() != null) {
            roles.getClient()
                    .keySet()
                    .forEach(clientId -> context.captureState(
                            ClientRoleReconciler.stateScope(clientId), this.stateService.getClientRoles(clientId)));
        }
    }

    private DesiredState desiredStateOf(final RealmImport realmImport, final boolean realmExists) {
        DesiredState desired = new DesiredState();
        if (realmImport.getClients() != null) {
            desired.declare(ResourceType.CLIENT, clientsOf(realmImport, realmExists));
        }
        ClientProfilesRepresentation clientProfiles = realmImport.getParsedClientProfiles();
        ClientPoliciesRepresentation clientPolicies = realmImport.getParsedClientPolicies();
        // Policies refer to profiles: a file declaring either declares both, the missing one as empty, as before
        if (clientProfiles != null || clientPolicies != null) {
            desired.declare(
                    ResourceType.CLIENT_PROFILE,
                    clientProfiles == null || clientProfiles.getProfiles() == null
                            ? List.of()
                            : clientProfiles.getProfiles().stream()
                                    .map(this.clientPolicyMapper::toProfile)
                                    .toList());
            desired.declare(
                    ResourceType.CLIENT_POLICY,
                    clientPolicies == null || clientPolicies.getPolicies() == null
                            ? List.of()
                            : clientPolicies.getPolicies().stream()
                                    .map(this.clientPolicyMapper::toPolicy)
                                    .toList());
        }
        if (realmImport.getScopeMappings() != null) {
            desired.declare(
                    ResourceType.SCOPE_MAPPING,
                    realmImport.getScopeMappings().stream()
                            .map(mapping -> scopeMappingOf(mapping, null))
                            .flatMap(Optional::stream)
                            .toList());
        }
        if (realmImport.getClientScopeMappings() != null) {
            List<ScopeMapping> clientScopeMappings = new ArrayList<>();
            realmImport.getClientScopeMappings().forEach((roleClientId, mappings) -> {
                if (mappings != null) {
                    mappings.forEach(
                            mapping -> scopeMappingOf(mapping, roleClientId).ifPresent(clientScopeMappings::add));
                }
            });
            desired.declare(ResourceType.CLIENT_SCOPE_MAPPING, clientScopeMappings);
        }
        if (realmImport.getIdentityProviders() != null) {
            desired.declare(
                    ResourceType.IDENTITY_PROVIDER,
                    realmImport.getIdentityProviders().stream()
                            .map(this.identityProviderMapper::toIdentityProvider)
                            .toList());
        }
        if (realmImport.getIdentityProviderMappers() != null) {
            desired.declare(
                    ResourceType.IDENTITY_PROVIDER_MAPPER,
                    realmImport.getIdentityProviderMappers().stream()
                            .map(this.identityProviderMapper::toMapper)
                            .toList());
        }
        if (realmImport.getComponents() != null) {
            List<io.github.doriangrelu.keycloak.config.domain.component.model.Component> components =
                    this.componentMapper.flatten(realmImport.getComponents());
            desired.declare(
                    ResourceType.COMPONENT,
                    components.stream()
                            .filter(component -> component.key().isTopLevel())
                            .toList());
            // The sub-components of every declared component are managed, even when it declares none
            desired.declare(
                    ResourceType.SUB_COMPONENT,
                    components.stream()
                            .filter(component -> !component.key().isTopLevel())
                            .toList(),
                    components.stream().map(component -> component.key().path()).collect(Collectors.toSet()));
        }
        String userProfileEnabled = realmImport.getAttributesOrEmpty().get(USER_PROFILE_ENABLED);
        if (userProfileEnabled != null) {
            // As before, the configuration is only managed when the realm declares this attribute
            desired.declare(
                    ResourceType.USER_PROFILE,
                    List.of(
                            Boolean.parseBoolean(userProfileEnabled)
                                    ? this.userProfileMapper.toUserProfile(realmImport.getUserProfile())
                                    : UserProfile.byDefault()));
        }
        if (realmImport.getUsers() != null && !realmImport.getUsers().isEmpty()) {
            desired.declare(ResourceType.USER, usersOf(realmImport, realmExists));
        }
        if (realmImport.getGroups() != null) {
            List<Group> groups = new ArrayList<>();
            flattenGroups(realmImport.getGroups(), null, groups);
            desired.declare(ResourceType.GROUP, groups);
        }
        if (realmImport.getClientScopes() != null) {
            desired.declare(
                    ResourceType.CLIENT_SCOPE,
                    realmImport.getClientScopes().stream()
                            .map(scope -> this.clientScopeMapper.toClientScope(scope, false))
                            .toList());
        }
        if (realmImport.getMessageBundles() != null) {
            desired.declare(
                    ResourceType.MESSAGE_BUNDLE,
                    realmImport.getMessageBundles().entrySet().stream()
                            .map(locale -> new MessageBundle(locale.getKey(), locale.getValue()))
                            .toList());
        }
        if (realmImport.getRequiredActions() != null) {
            desired.declare(
                    ResourceType.REQUIRED_ACTION,
                    realmImport.getRequiredActions().stream()
                            .map(this.requiredActionMapper::toRequiredAction)
                            .toList());
        }
        RolesRepresentation roles = realmImport.getRoles();
        if (roles == null) {
            return desired;
        }
        if (roles.getRealm() != null) {
            desired.declare(
                    ResourceType.REALM_ROLE,
                    roles.getRealm().stream()
                            .map(role -> this.roleMapper.toRole(role, null))
                            .toList());
        }
        List<RoleComposites> composites = new ArrayList<>();
        if (roles.getRealm() != null) {
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
            desired.declare(
                    ResourceType.CLIENT_ROLE, clientRoles, roles.getClient().keySet());
        }
        if (!composites.isEmpty()) {
            desired.declare(ResourceType.ROLE_COMPOSITE, composites);
        }
        return desired;
    }

    /**
     * Clients of a file: authorization settings are checked on the client as written, a client only given by name is
     * resolved to its client id, and the admin-permissions client is left to Keycloak with fine-grained admin
     * permissions v2.
     */
    private List<Client> clientsOf(final RealmImport realmImport, final boolean realmExists) {
        String realm = realmImport.getRealm();
        Map<String, String> clientIdsByName = new HashMap<>();
        if (realmExists && realmImport.getClients().stream().anyMatch(client -> client.getClientId() == null)) {
            this.clientApi.listClients(realm).stream()
                    .filter(client -> client.getName() != null)
                    .forEach(client -> clientIdsByName.putIfAbsent(client.getName(), client.getClientId()));
        }
        List<Client> clients = new ArrayList<>();
        for (ClientRepresentation representation : realmImport.getClients()) {
            Client client = this.clientMapper.toClient(representation);
            if (this.properties.isValidate() && representation.getAuthorizationSettings() != null) {
                client.requireSupportForAuthorizationSettings(realm);
            }
            if (client.clientId() == null && clientIdsByName.containsKey(client.name())) {
                client = client.withClientId(clientIdsByName.get(client.name()));
            }
            if (isManagedByKeycloak(client)) {
                logger.info(
                        "Skipping 'admin-permissions' client in realm '{}' - FGAP V2 is active and this client is"
                                + " system-managed by Keycloak. Use 'adminPermissionsEnabled: true' at realm level"
                                + " instead.",
                        realm);
                continue;
            }
            clients.add(client);
        }
        return clients;
    }

    /**
     * Users of a file. When the realm uses the email as username, a user is identified by its email: a different
     * username is refused. Service accounts keep their username.
     */
    private List<User> usersOf(final RealmImport realmImport, final boolean realmExists) {
        String realm = realmImport.getRealm();
        Boolean declared = realmImport.isRegistrationEmailAsUsername();
        boolean emailAsUsername = declared != null
                ? declared
                : realmExists
                        && realmImport.getUsers().stream().anyMatch(user -> user.getServiceAccountClientId() == null)
                        && Boolean.TRUE.equals(this.keycloakProvider
                                .getInstance()
                                .realm(realm)
                                .toRepresentation()
                                .isRegistrationEmailAsUsername());
        return realmImport.getUsers().stream()
                .map(user -> this.userMapper.toUser(user, usernameOf(realm, user, emailAsUsername)))
                .toList();
    }

    private static String usernameOf(final String realm, final UserRepresentation user, final boolean emailAsUsername) {
        boolean serviceAccount = user.getServiceAccountClientId() != null
                && !user.getServiceAccountClientId().isEmpty();
        if (emailAsUsername && !serviceAccount) {
            if (user.getUsername() != null && !Objects.equals(user.getUsername(), user.getEmail())) {
                throw new InvalidUserException(String.format(
                        "Invalid user '%s' in realm '%s': username (%s) and email (%s) "
                                + "is different while 'email as username' is enabled on realm.",
                        user.getUsername(), realm, user.getUsername(), user.getEmail()));
            }
            if (user.getEmail() != null) {
                return user.getEmail();
            }
        }
        if (user.getUsername() == null) {
            throw new InvalidUserException(String.format("Invalid user in realm '%s': no username", realm));
        }
        return user.getUsername();
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

    /** Nested groups of a file, one per path, parents first. */
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

    private boolean isManagedByKeycloak(final Client client) {
        boolean adminPermissions =
                Client.ADMIN_PERMISSIONS.equals(client.clientId()) || Client.ADMIN_PERMISSIONS.equals(client.name());
        return adminPermissions && this.keycloakProvider.isFgapV2Active();
    }

    private static List<ProtectionRule> protectionsOf(final RealmImport realmImport) {
        return realmImport.getProtectedResources().stream()
                .map(LegacyReconciliationBridge::toRule)
                .toList();
    }

    private static ProtectionRule toRule(final ProtectedResource resource) {
        ProtectionRule.Matching matching = resource.rule() == ProtectedResource.Rule.PATTERN
                ? ProtectionRule.Matching.PATTERN
                : ProtectionRule.Matching.EQUALS;
        return new ProtectionRule(matching, resource.matcher());
    }
}
