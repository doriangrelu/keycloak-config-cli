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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientauthorization;

import io.github.doriangrelu.keycloak.config.application.clientauthorization.port.ClientAuthorizationGateway;
import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.CloneUtil;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.JsonUtil;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientAuthorizationApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ClientAuthorizationGateway} on the Keycloak Admin API.
 *
 * <p>With the fine-grained admin permissions of version 2 (Keycloak 26.2+), Keycloak manages the
 * {@code admin-permissions} client itself and answers {@code 400}, {@code 404} or {@code 501} to the authorization API
 * of some clients: those answers are logged and the import goes on, as the legacy import did.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ClientAuthorizationKeycloakGateway implements ClientAuthorizationGateway {

    private static final Logger logger = LoggerFactory.getLogger(ClientAuthorizationKeycloakGateway.class);

    static final String REALM_MANAGEMENT = "realm-management";
    static final String ADMIN_PERMISSIONS = "admin-permissions";
    static final String DEFAULT_RESOURCE = "Default Resource";

    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int NOT_IMPLEMENTED = 501;
    private static final String V2_RESOURCE_TYPES = "^(Groups|Users|Clients|Roles)$";

    /** Resource types of the permissions of version 2, by permission type of version 1. */
    private static final Map<String, String> PERMISSION_TYPES = Map.of(
            "Clients", FineGrainedPermissions.CLIENT,
            "Groups", FineGrainedPermissions.GROUP,
            "Users", "user",
            "Roles", FineGrainedPermissions.ROLE,
            "IdentityProviders", FineGrainedPermissions.IDENTITY_PROVIDER);

    private final ClientAuthorizationApi authorizationApi;
    private final ClientApi clientApi;
    private final GroupApi groupApi;
    private final RoleApi roleApi;
    private final IdentityProviderApi identityProviderApi;
    private final UnmodeledAttributesConverter unmodeledAttributes;

    public ClientAuthorizationKeycloakGateway(
            final ClientAuthorizationApi authorizationApi,
            final ClientApi clientApi,
            final GroupApi groupApi,
            final RoleApi roleApi,
            final IdentityProviderApi identityProviderApi,
            final UnmodeledAttributesConverter unmodeledAttributes) {
        this.authorizationApi = authorizationApi;
        this.clientApi = clientApi;
        this.groupApi = groupApi;
        this.roleApi = roleApi;
        this.identityProviderApi = identityProviderApi;
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /** Answers of Keycloak when the authorization API of a client is not available. */
    static boolean isFgapV2Error(final KeycloakAccessException error) {
        return error.getStatus() == NOT_FOUND || error.getStatus() == NOT_IMPLEMENTED;
    }

    private static boolean isUnsupported(final KeycloakAccessException error) {
        return isFgapV2Error(error) || error.getStatus() == BAD_REQUEST;
    }

    @Override
    public Optional<ClientAuthorization> find(final String realm, final ClientAuthorization client) {
        return findClient(realm, client)
                .map(existing -> new ClientAuthorization(
                        client.clientId(),
                        client.name(),
                        this.unmodeledAttributes.extract(existingSettings(realm, existing), Set.of())));
    }

    private Optional<ClientRepresentation> findClient(final String realm, final ClientAuthorization client) {
        if (client.clientId() != null) {
            return this.clientApi.findByClientId(realm, client.clientId());
        }
        return this.clientApi.listClients(realm).stream()
                .filter(candidate -> Objects.equals(candidate.getName(), client.name()))
                .findFirst();
    }

    @Override
    public void apply(final String realm, final ClientAuthorization target) {
        ClientRepresentation client = findClient(realm, target)
                .orElseThrow(() ->
                        new IllegalStateException("Client '" + target.key() + "' is missing in realm '" + realm + "'"));
        boolean fgapV2 = isFgapV2Active(realm);
        if (fgapV2 && ADMIN_PERMISSIONS.equals(client.getClientId())) {
            logger.info(
                    "Skipping authorization settings for 'admin-permissions' client in realm '{}' - FGAP V2 manages"
                            + " this client internally and blocks API access"
                            + " (see https://github.com/keycloak/keycloak/issues/43977). Remove this client from your"
                            + " import configuration and use realm-level adminPermissionsEnabled flag instead.",
                    realm);
            return;
        }
        new Application(realm, client, fgapV2, settingsOf(target)).run();
    }

    private ResourceServerRepresentation settingsOf(final ClientAuthorization target) {
        ResourceServerRepresentation settings = new ResourceServerRepresentation();
        this.unmodeledAttributes.apply(target.settings(), settings);
        if (settings.getResources() == null) {
            settings.setResources(new ArrayList<>());
        }
        if (settings.getPolicies() == null) {
            settings.setPolicies(new ArrayList<>());
        }
        if (settings.getScopes() == null) {
            settings.setScopes(new ArrayList<>());
        }
        return settings;
    }

    private boolean isFgapV2Active(final String realm) {
        try {
            return this.authorizationApi.isFgapV2Active();
        } catch (final RuntimeException error) {
            logger.warn(
                    "Unable to detect FGAP V2 status for realm '{}', falling back to V1 behavior: {}",
                    realm,
                    error.getMessage());
            return false;
        }
    }

    /** Current settings, or empty settings when Keycloak does not give them. */
    private ResourceServerRepresentation existingSettings(final String realm, final ClientRepresentation client) {
        try {
            return this.authorizationApi.exportSettings(realm, client.getId());
        } catch (final KeycloakAccessException error) {
            if (!isUnsupported(error)) {
                throw error;
            }
            logger.debug(
                    "Cannot get authorization settings for client '{}' in realm '{}' (HTTP {}) - This is expected"
                            + " for FGAP V2 admin-permissions client or clients without authorization support",
                    identifierOf(client),
                    realm,
                    error.getStatus());
            ResourceServerRepresentation empty = new ResourceServerRepresentation();
            empty.setClientId(client.getId());
            empty.setResources(new ArrayList<>());
            empty.setPolicies(new ArrayList<>());
            empty.setScopes(new ArrayList<>());
            return empty;
        }
    }

    private static String identifierOf(final ClientRepresentation client) {
        return client.getName() != null && !client.getName().startsWith("${") ? client.getName() : client.getClientId();
    }

    /** One application of the authorization of one client. */
    private final class Application {

        private final String realm;
        private final ClientRepresentation client;
        private final boolean fgapV2;
        private final ResourceServerRepresentation declared;
        private final FineGrainedPermissions permissions;

        private Application(
                final String realm,
                final ClientRepresentation client,
                final boolean fgapV2,
                final ResourceServerRepresentation declared) {
            this.realm = realm;
            this.client = client;
            this.fgapV2 = fgapV2;
            this.declared = declared;
            this.permissions = new FineGrainedPermissions(
                    realm,
                    fgapV2,
                    ClientAuthorizationKeycloakGateway.this.authorizationApi,
                    ClientAuthorizationKeycloakGateway.this.clientApi,
                    ClientAuthorizationKeycloakGateway.this.groupApi,
                    ClientAuthorizationKeycloakGateway.this.roleApi,
                    ClientAuthorizationKeycloakGateway.this.identityProviderApi);
        }

        void run() {
            if (REALM_MANAGEMENT.equals(this.client.getClientId())) {
                this.permissions.enableFor(this.declared.getResources());
            }
            ResourceServerRepresentation existing = existingSettings(this.realm, this.client);
            updateSettings(existing);
            List<ResourceRepresentation> resources =
                    this.declared.getResources().stream().map(this::sanitized).toList();
            List<PolicyRepresentation> policies =
                    this.declared.getPolicies().stream().map(this::sanitized).toList();
            List<ScopeRepresentation> scopes = this.declared.getScopes();

            createOrUpdateResources(listOf(existing.getResources()), resources);
            createOrUpdateScopes(listOf(existing.getScopes()), scopes);
            List<ResourceRepresentation> deletable = listOf(existing.getResources());
            Set<String> scopesOfKeptResources = Set.of();
            if (managesResourcesRemotely(existing)) {
                // Only the Default Resource of Keycloak: the others may have been created by the application
                deletable = deletable.stream()
                        .filter(resource -> DEFAULT_RESOURCE.equals(resource.getName()))
                        .toList();
                scopesOfKeptResources = scopesOfUndeclared(
                        listOf(existing.getResources()).stream()
                                .filter(resource -> !DEFAULT_RESOURCE.equals(resource.getName()))
                                .toList(),
                        resources);
            }
            deleteResources(deletable, resources);
            deletePolicies(listOf(existing.getPolicies()), policies);
            deleteScopes(listOf(existing.getScopes()), scopes, scopesOfKeptResources);
            createOrUpdatePolicies(listOf(refreshed(existing).getPolicies()), policies);
        }

        /**
         * Whether the application may create its resources at runtime, through the Protection API: they are runtime
         * data, as users, and the import only deletes the {@code Default Resource} of Keycloak among the resources it
         * does not declare. Keycloak allows it by default: {@code allowRemoteResourceManagement: false} makes the files
         * the only source of truth for the resources.
         */
        private boolean managesResourcesRemotely(final ResourceServerRepresentation existing) {
            return this.declared.isAllowRemoteResourceManagement() || existing.isAllowRemoteResourceManagement();
        }

        private static <T> List<T> listOf(final List<T> list) {
            return list == null ? List.of() : list;
        }

        private void updateSettings(final ResourceServerRepresentation existing) {
            if (CloneUtil.deepEquals(
                    this.declared, existing, "clientId", "policies", "resources", "permissions", "scopes")) {
                return;
            }
            ResourceServerRepresentation patched = CloneUtil.patch(existing, this.declared);
            patched.setId(this.client.getClientId());
            logger.debug(
                    "Update authorization settings for client '{}' in realm '{}'",
                    identifierOf(this.client),
                    this.realm);
            try {
                ClientAuthorizationKeycloakGateway.this.authorizationApi.updateSettings(
                        this.realm, this.client.getId(), patched);
            } catch (final KeycloakAccessException error) {
                boolean adminPermissionsRefused =
                        error.getStatus() == BAD_REQUEST && ADMIN_PERMISSIONS.equals(this.client.getClientId());
                if (!isFgapV2Error(error) && !adminPermissionsRefused) {
                    throw error;
                }
                logger.debug(
                        "Client '{}' in realm '{}' does not support authorization settings updates - This is normal"
                                + " for FGAP V2 or clients without authorization support",
                        identifierOf(this.client),
                        this.realm);
            }
        }

        private ResourceServerRepresentation refreshed(final ResourceServerRepresentation existing) {
            try {
                return ClientAuthorizationKeycloakGateway.this.authorizationApi.exportSettings(
                        this.realm, this.client.getId());
            } catch (final KeycloakAccessException error) {
                if (!isUnsupported(error)) {
                    throw error;
                }
                logger.debug(
                        "Cannot refresh authorization config for client '{}' in realm '{}' (HTTP {}) - Using existing"
                                + " authorization settings.",
                        identifierOf(this.client),
                        this.realm,
                        error.getStatus());
                return existing;
            }
        }

        private ResourceRepresentation sanitized(final ResourceRepresentation resource) {
            resource.setName(this.permissions.sanitizedResourceName(resource.getName()));
            return resource;
        }

        private PolicyRepresentation sanitized(final PolicyRepresentation policy) {
            policy.setName(this.permissions.sanitizedPolicyName(policy.getName()));
            Map<String, String> config = policy.getConfig();
            if (config != null
                    && config.containsKey("resources")
                    && config.get("resources").contains("$")) {
                List<String> resources = JsonUtil.fromJson(config.get("resources"));
                String defaultResourceType = config.get("defaultResourceType");
                config.put(
                        "resources",
                        JsonUtil.toJson(resources.stream()
                                .map(resource -> sanitizedPolicyResource(resource, defaultResourceType))
                                .toList()));
            }
            return policy;
        }

        /** A resource a policy applies to: a full resource name, or a bare placeholder of its default type. */
        private String sanitizedPolicyResource(final String resource, final String defaultResourceType) {
            if (resource.contains(".resource.")) {
                return this.permissions.sanitizedResourceName(resource);
            }
            if (!resource.startsWith("$")) {
                return resource;
            }
            if (defaultResourceType == null || defaultResourceType.isEmpty()) {
                logger.warn(
                        "Found bare placeholder '{}' but no defaultResourceType specified in policy config, skipping"
                                + " transformation",
                        resource);
                return resource;
            }
            String permissionType = PERMISSION_TYPES.get(defaultResourceType);
            if (permissionType == null) {
                logger.warn(
                        "Unknown defaultResourceType '{}' for bare placeholder '{}', skipping transformation",
                        defaultResourceType,
                        resource);
                return resource;
            }
            return this.permissions.sanitizedResourceName(permissionType + ".resource." + resource);
        }

        private void createOrUpdateResources(
                final List<ResourceRepresentation> existing, final List<ResourceRepresentation> declared) {
            Map<String, ResourceRepresentation> byName = byName(existing, ResourceRepresentation::getName);
            for (ResourceRepresentation resource : declared) {
                ResourceRepresentation current = byName.get(resource.getName());
                if (current == null) {
                    createResource(resource);
                } else {
                    updateResource(current, resource);
                }
            }
        }

        private void createResource(final ResourceRepresentation resource) {
            ownerById(resource);
            logger.debug(
                    "Create authorization resource '{}' for client '{}' in realm '{}'",
                    resource.getName(),
                    identifierOf(this.client),
                    this.realm);
            try {
                ClientAuthorizationKeycloakGateway.this.authorizationApi.createResource(
                        this.realm, this.client.getId(), resource);
            } catch (final KeycloakAccessException error) {
                if (!isUnsupported(error)) {
                    throw error;
                }
                if (ADMIN_PERMISSIONS.equals(this.client.getClientId())
                        && resource.getName().matches(V2_RESOURCE_TYPES)) {
                    logger.debug("Skipping V2 resource type '{}' - auto-managed by Keycloak", resource.getName());
                } else {
                    warnUnsupported("create", "resource", resource.getName());
                }
            }
        }

        private void updateResource(final ResourceRepresentation current, final ResourceRepresentation declared) {
            if (current.getOwner() != null
                    && current.getOwner().getId() == null
                    && declared.getOwner() != null
                    && Objects.equals(
                            current.getOwner().getName(), declared.getOwner().getId())) {
                current.getOwner().setId(declared.getOwner().getId());
                current.getOwner().setName(null);
            }
            if (current.getAttributes() != null
                    && current.getAttributes().isEmpty()
                    && declared.getAttributes() == null) {
                current.setAttributes(null);
            }
            if (CloneUtil.deepEquals(declared, current, "id", "_id")) {
                return;
            }
            ownerById(declared);
            declared.setId(current.getId());
            logger.debug(
                    "Update authorization resource '{}' for client '{}' in realm '{}'",
                    declared.getName(),
                    identifierOf(this.client),
                    this.realm);
            tolerating(
                    () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.updateResource(
                            this.realm, this.client.getId(), declared),
                    "update",
                    "resource",
                    declared.getName());
        }

        /** Keycloak expects the owner of a resource by id; an import may give it by name. */
        private static void ownerById(final ResourceRepresentation resource) {
            if (resource.getOwner() != null
                    && resource.getOwner().getId() == null
                    && resource.getOwner().getName() != null) {
                resource.getOwner().setId(resource.getOwner().getName());
                resource.getOwner().setName(null);
            }
        }

        private void deleteResources(
                final List<ResourceRepresentation> existing, final List<ResourceRepresentation> declared) {
            Set<String> declaredNames =
                    declared.stream().map(ResourceRepresentation::getName).collect(Collectors.toSet());
            existing.stream()
                    .filter(resource -> !declaredNames.contains(resource.getName()))
                    .forEach(resource -> {
                        logger.debug(
                                "Remove authorization resource '{}' for client '{}' in realm '{}'",
                                resource.getName(),
                                identifierOf(this.client),
                                this.realm);
                        tolerating(
                                () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.deleteResource(
                                        this.realm, this.client.getId(), resource.getName()),
                                "remove",
                                "resource",
                                resource.getName());
                    });
        }

        private void createOrUpdateScopes(
                final List<ScopeRepresentation> existing, final List<ScopeRepresentation> declared) {
            Map<String, ScopeRepresentation> byName = byName(existing, ScopeRepresentation::getName);
            for (ScopeRepresentation scope : declared) {
                ScopeRepresentation current = byName.get(scope.getName());
                if (current == null) {
                    logger.debug(
                            "Add authorization scope '{}' for client '{}' in realm '{}'",
                            scope.getName(),
                            identifierOf(this.client),
                            this.realm);
                    creating(
                            () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.createScope(
                                    this.realm, this.client.getId(), scope),
                            "scope",
                            scope.getName());
                } else if (!CloneUtil.deepEquals(scope, current, "id")) {
                    scope.setId(current.getId());
                    logger.debug(
                            "Update authorization scope '{}' for client '{}' in realm '{}'",
                            scope.getName(),
                            identifierOf(this.client),
                            this.realm);
                    tolerating(
                            () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.updateScope(
                                    this.realm, this.client.getId(), scope),
                            "update",
                            "scope",
                            scope.getName());
                }
            }
        }

        /** Scopes of the resources the import does not declare: Keycloak keeps a scope a resource still uses. */
        private static Set<String> scopesOfUndeclared(
                final List<ResourceRepresentation> existing, final List<ResourceRepresentation> declared) {
            Set<String> declaredNames =
                    declared.stream().map(ResourceRepresentation::getName).collect(Collectors.toSet());
            return existing.stream()
                    .filter(resource -> !declaredNames.contains(resource.getName()))
                    .filter(resource -> resource.getScopes() != null)
                    .flatMap(resource -> resource.getScopes().stream())
                    .map(ScopeRepresentation::getName)
                    .collect(Collectors.toSet());
        }

        private void deleteScopes(
                final List<ScopeRepresentation> existing,
                final List<ScopeRepresentation> declared,
                final Set<String> stillUsed) {
            Set<String> declaredNames =
                    declared.stream().map(ScopeRepresentation::getName).collect(Collectors.toSet());
            existing.stream()
                    .filter(scope -> !declaredNames.contains(scope.getName()))
                    .filter(scope -> !stillUsed.contains(scope.getName()))
                    .forEach(scope -> tolerating(
                            () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.deleteScope(
                                    this.realm, this.client.getId(), scope.getName()),
                            "remove",
                            "scope",
                            scope.getName()));
        }

        private void createOrUpdatePolicies(
                final List<PolicyRepresentation> existing, final List<PolicyRepresentation> declared) {
            Map<String, PolicyRepresentation> byName = byName(existing, PolicyRepresentation::getName);
            for (PolicyRepresentation policy : declared) {
                PolicyRepresentation current = byName.get(policy.getName());
                if (current == null) {
                    logger.debug(
                            "Create authorization policy '{}' for client '{}' in realm '{}'",
                            policy.getName(),
                            identifierOf(this.client),
                            this.realm);
                    creating(
                            () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.createPolicy(
                                    this.realm, this.client.getId(), policy),
                            "policy",
                            policy.getName());
                } else if (!CloneUtil.deepEquals(policy, current, "id")) {
                    policy.setId(current.getId());
                    logger.debug(
                            "Update authorization policy '{}' for client '{}' in realm '{}'",
                            policy.getName(),
                            identifierOf(this.client),
                            this.realm);
                    tolerating(
                            () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.updatePolicy(
                                    this.realm, this.client.getId(), policy),
                            "update",
                            "policy",
                            policy.getName());
                }
            }
        }

        private void deletePolicies(
                final List<PolicyRepresentation> existing, final List<PolicyRepresentation> declared) {
            Set<String> declaredNames =
                    declared.stream().map(PolicyRepresentation::getName).collect(Collectors.toSet());
            existing.stream()
                    .filter(policy -> !declaredNames.contains(policy.getName()))
                    .forEach(policy -> {
                        logger.debug(
                                "Remove authorization policy '{}' for client '{}' in realm '{}'",
                                policy.getName(),
                                identifierOf(this.client),
                                this.realm);
                        // A policy depending on a deleted one is deleted with it
                        tolerating(
                                () -> ClientAuthorizationKeycloakGateway.this.authorizationApi.deletePolicy(
                                        this.realm, this.client.getId(), policy.getName()),
                                "remove",
                                "policy",
                                policy.getName());
                    });
        }

        /** A creation Keycloak refuses because the authorization API of the client is not available. */
        private void creating(final Runnable creation, final String kind, final String name) {
            try {
                creation.run();
            } catch (final KeycloakAccessException error) {
                if (!isUnsupported(error)) {
                    throw error;
                }
                warnUnsupported("create", kind, name);
            }
        }

        private void tolerating(final Runnable call, final String action, final String kind, final String name) {
            try {
                call.run();
            } catch (final KeycloakAccessException error) {
                if (!isFgapV2Error(error)) {
                    throw error;
                }
                warnUnsupported(action, kind, name);
            }
        }

        private void warnUnsupported(final String action, final String kind, final String name) {
            logger.warn(
                    "Cannot {} authorization {} '{}' for client '{}' - {}",
                    action,
                    kind,
                    name,
                    identifierOf(this.client),
                    this.fgapV2
                            ? "FGAP V2 is active (authorization managed at realm level)."
                            : "Authorization API not supported.");
        }

        private static <T> Map<String, T> byName(final List<T> items, final Function<T, String> name) {
            return items.stream().collect(Collectors.toMap(name, Function.identity(), (first, second) -> first));
        }
    }
}
