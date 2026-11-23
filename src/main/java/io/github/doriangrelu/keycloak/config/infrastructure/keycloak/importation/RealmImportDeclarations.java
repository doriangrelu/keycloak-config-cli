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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.RealmDeclaration;
import io.github.doriangrelu.keycloak.config.application.importation.port.RealmDeclarationParser;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import io.github.doriangrelu.keycloak.config.domain.user.model.InvalidUserException;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.domain.userprofile.model.UserProfile;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.authenticationflow.AuthenticationFlowKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy.ClientPolicyKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.component.ComponentKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group.GroupKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider.IdentityProviderKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm.RealmKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction.RequiredActionKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role.RoleKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.user.UserKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.userprofile.UserProfileKeycloakMapper;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import io.github.doriangrelu.keycloak.config.util.CloneUtil;
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
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads the realms of the import files, in the format of Keycloak ({@link RealmImport}), and translates what they
 * declare into the desired state of the reconciliation engine.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RealmImportDeclarations implements RealmDeclarationParser {

    private static final Logger logger = LoggerFactory.getLogger(RealmImportDeclarations.class);

    /** Realm attribute that enables the management of the user profile configuration. */
    static final String USER_PROFILE_ENABLED = "userProfileEnabled";

    private final RealmImportParser parser;

    private final RoleKeycloakMapper roleMapper;
    private final ClientKeycloakMapper clientMapper;
    private final GroupKeycloakMapper groupMapper;
    private final UserKeycloakMapper userMapper;
    private final IdentityProviderKeycloakMapper identityProviderMapper;
    private final ClientPolicyKeycloakMapper clientPolicyMapper;
    private final ComponentKeycloakMapper componentMapper;
    private final AuthenticationFlowKeycloakMapper authenticationFlowMapper;
    private final UnmodeledAttributesConverter unmodeledAttributes;
    private final RealmKeycloakMapper realmMapper;
    private final UserProfileKeycloakMapper userProfileMapper;
    private final ClientApi clientApi;
    private final KeycloakProvider keycloakProvider;
    private final ClientScopeKeycloakMapper clientScopeMapper;
    private final RequiredActionKeycloakMapper requiredActionMapper;
    private final ImportConfigProperties properties;

    public RealmImportDeclarations(
            final RealmImportParser parser,
            final RoleKeycloakMapper roleMapper,
            final ClientKeycloakMapper clientMapper,
            final GroupKeycloakMapper groupMapper,
            final UserKeycloakMapper userMapper,
            final IdentityProviderKeycloakMapper identityProviderMapper,
            final ClientPolicyKeycloakMapper clientPolicyMapper,
            final ComponentKeycloakMapper componentMapper,
            final AuthenticationFlowKeycloakMapper authenticationFlowMapper,
            final UnmodeledAttributesConverter unmodeledAttributes,
            final RealmKeycloakMapper realmMapper,
            final UserProfileKeycloakMapper userProfileMapper,
            final ClientApi clientApi,
            final KeycloakProvider keycloakProvider,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final RequiredActionKeycloakMapper requiredActionMapper,
            final ImportConfigProperties properties) {
        this.parser = parser;
        this.roleMapper = roleMapper;
        this.clientMapper = clientMapper;
        this.groupMapper = groupMapper;
        this.userMapper = userMapper;
        this.identityProviderMapper = identityProviderMapper;
        this.clientPolicyMapper = clientPolicyMapper;
        this.componentMapper = componentMapper;
        this.authenticationFlowMapper = authenticationFlowMapper;
        this.unmodeledAttributes = unmodeledAttributes;
        this.realmMapper = realmMapper;
        this.userProfileMapper = userProfileMapper;
        this.clientApi = clientApi;
        this.keycloakProvider = keycloakProvider;
        this.clientScopeMapper = clientScopeMapper;
        this.requiredActionMapper = requiredActionMapper;
        this.properties = properties;
    }

    @Override
    public List<RealmDeclaration> parse(final ImportFile file) {
        return this.parser.parse(file).stream().map(this::declarationOf).toList();
    }

    /** The declaration of a realm already read. */
    public RealmDeclaration declarationOf(final RealmImport realmImport) {
        if (realmImport.getUsers() != null && realmImport.getUsers().isEmpty()) {
            logger.warn("Purging users isn't supported in keycloak-config-cli!");
        }
        return new Declaration(realmImport);
    }

    /** A realm of an import file: its desired state is computed when the import needs it. */
    private final class Declaration implements RealmDeclaration {

        private static final String USER_STORAGE_PROVIDER = "org.keycloak.storage.UserStorageProvider";

        private final RealmImport realmImport;

        private Declaration(final RealmImport realmImport) {
            this.realmImport = realmImport;
        }

        @Override
        public String realm() {
            return this.realmImport.getRealm();
        }

        @Override
        public String source() {
            return this.realmImport.getSource();
        }

        @Override
        public List<ProtectionRule> protections() {
            return this.realmImport.getProtectedResources();
        }

        @Override
        public DesiredState settings() {
            return realmSettingsStateOf(this.realmImport, new DesiredState());
        }

        @Override
        public DesiredState desiredState(final boolean realmExists) {
            return desiredStateOf(this.realmImport, realmExists);
        }

        @Override
        public boolean declaresUserStorage() {
            return this.realmImport.getComponents() != null
                    && this.realmImport.getComponents().containsKey(USER_STORAGE_PROVIDER);
        }
    }

    private List<ClientAuthorization> clientAuthorizationsOf(final RealmImport realmImport) {
        return realmImport.getClients().stream()
                .filter(client -> client.getAuthorizationSettings() != null)
                .filter(client -> client.getClientId() != null || client.getName() != null)
                .map(client -> new ClientAuthorization(
                        client.getClientId(),
                        client.getName(),
                        this.unmodeledAttributes.extract(client.getAuthorizationSettings(), Set.of())))
                .toList();
    }

    private DesiredState realmSettingsStateOf(final RealmImport realmImport, final DesiredState desired) {
        return desired.declare(
                ResourceType.REALM_SETTINGS,
                List.of(this.realmMapper.toSettings(CloneUtil.deepClone(realmImport, RealmRepresentation.class))));
    }

    private DesiredState desiredStateOf(final RealmImport realmImport, final boolean realmExists) {
        DesiredState desired = realmSettingsStateOf(realmImport, new DesiredState());
        List<DefaultClientScopes> defaultClientScopes = new ArrayList<>();
        if (realmImport.getDefaultDefaultClientScopes() != null) {
            defaultClientScopes.add(new DefaultClientScopes(
                    DefaultClientScopes.Kind.DEFAULT, Set.copyOf(realmImport.getDefaultDefaultClientScopes())));
        }
        if (realmImport.getDefaultOptionalClientScopes() != null) {
            defaultClientScopes.add(new DefaultClientScopes(
                    DefaultClientScopes.Kind.OPTIONAL, Set.copyOf(realmImport.getDefaultOptionalClientScopes())));
        }
        if (!defaultClientScopes.isEmpty()) {
            desired.declare(ResourceType.DEFAULT_CLIENT_SCOPE, defaultClientScopes);
        }
        if (realmImport.getAuthenticationFlows() != null) {
            desired.declare(
                    ResourceType.REALM_FLOW_BINDING,
                    List.of(io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm.RealmKeycloakGateway
                            .flowBindingsOf(realmImport)));
        }
        if (realmImport.getDefaultGroups() != null) {
            desired.declare(
                    ResourceType.DEFAULT_GROUP, List.of(new DefaultGroups(Set.copyOf(realmImport.getDefaultGroups()))));
        }
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
        if (realmImport.getAuthenticationFlows() != null) {
            desired.declare(
                    ResourceType.AUTHENTICATION_FLOW,
                    this.authenticationFlowMapper.toDesiredFlows(
                            realmImport.getAuthenticationFlows(), realmImport.getAuthenticatorConfig()));
        }
        if (realmImport.getAuthenticationFlows() != null || realmImport.getAuthenticatorConfig() != null) {
            desired.declare(
                    ResourceType.AUTHENTICATOR_CONFIG,
                    realmImport.getAuthenticatorConfig() == null
                            ? List.of()
                            : realmImport.getAuthenticatorConfig().stream()
                                    .map(config -> this.authenticationFlowMapper.toAuthenticatorConfig(config, false))
                                    .toList());
        }
        if (realmImport.getClients() != null) {
            desired.declare(ResourceType.CLIENT_AUTHORIZATION, clientAuthorizationsOf(realmImport));
        }
        if (realmImport.getClients() != null) {
            // The declared bindings of a client are exact: a client without bindings loses them, as before
            desired.declare(
                    ResourceType.CLIENT_FLOW_BINDING,
                    realmImport.getClients().stream()
                            .filter(client -> client.getClientId() != null || client.getName() != null)
                            .map(client -> new ClientFlowBindings(
                                    client.getClientId(),
                                    client.getName(),
                                    client.getAuthenticationFlowBindingOverrides()))
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
            // A component that declares sub-components has exactly those; one that declares none keeps its own, such
            // as the mappers Keycloak creates with an LDAP user storage: the files cannot tell an empty list apart
            List<io.github.doriangrelu.keycloak.config.domain.component.model.Component> children = components.stream()
                    .filter(component -> !component.key().isTopLevel())
                    .toList();
            desired.declare(
                    ResourceType.SUB_COMPONENT,
                    children,
                    children.stream()
                            .map(component -> component.key().parent().path())
                            .collect(Collectors.toSet()));
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
}
