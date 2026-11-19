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

import io.github.doriangrelu.keycloak.config.application.clientauthorization.UnknownAuthorizationReferenceException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientAuthorizationApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.List;
import java.util.Objects;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fine-grained admin permissions of {@code realm-management}, for one realm and one import. Their resources and
 * policies name Keycloak objects by id ({@code client.resource.<id>}); an import names them through a placeholder
 * instead ({@code client.resource.$<client id>}), resolved here: a client by client id, a group by path, a realm role
 * by name, an identity provider by alias. With the permissions of version 2, a resource is named by the id alone.
 */
final class FineGrainedPermissions {

    private static final Logger logger = LoggerFactory.getLogger(FineGrainedPermissions.class);

    static final String CLIENT = "client";
    static final String GROUP = "group";
    static final String ROLE = "role";
    static final String IDENTITY_PROVIDER = "idp";

    private final String realm;
    private final boolean fgapV2;
    private final ClientAuthorizationApi authorizationApi;
    private final ClientApi clientApi;
    private final GroupApi groupApi;
    private final RoleApi roleApi;
    private final IdentityProviderApi identityProviderApi;
    private List<IdentityProviderRepresentation> identityProviders;

    FineGrainedPermissions(
            final String realm,
            final boolean fgapV2,
            final ClientAuthorizationApi authorizationApi,
            final ClientApi clientApi,
            final GroupApi groupApi,
            final RoleApi roleApi,
            final IdentityProviderApi identityProviderApi) {
        this.realm = realm;
        this.fgapV2 = fgapV2;
        this.authorizationApi = authorizationApi;
        this.clientApi = clientApi;
        this.groupApi = groupApi;
        this.roleApi = roleApi;
        this.identityProviderApi = identityProviderApi;
    }

    /** Enables the permissions of each object a resource of {@code realm-management} names. */
    void enableFor(final List<ResourceRepresentation> resources) {
        for (ResourceRepresentation resource : resources) {
            PermissionTypeAndId typeAndId = PermissionTypeAndId.fromResourceName(resource.getName());
            if (typeAndId != null) {
                String id = resolveObjectId(typeAndId, resource.getName());
                enable(typeAndId.type, id, resource.getName());
            }
        }
    }

    private void enable(final String type, final String id, final String authzName) {
        try {
            switch (type) {
                case CLIENT -> {
                    if (!this.authorizationApi.isClientPermissionEnabled(this.realm, id)) {
                        this.authorizationApi.enableClientPermission(this.realm, id);
                    }
                }
                case GROUP -> {
                    if (!this.authorizationApi.isGroupPermissionEnabled(this.realm, id)) {
                        this.authorizationApi.enableGroupPermission(this.realm, id);
                    }
                }
                case ROLE -> {
                    if (!this.authorizationApi.isRealmRolePermissionEnabled(this.realm, id)) {
                        this.authorizationApi.enableRealmRolePermission(this.realm, id);
                    }
                }
                case IDENTITY_PROVIDER -> {
                    String alias = identityProviderAliasOf(id);
                    if (!this.authorizationApi.isIdentityProviderPermissionEnabled(this.realm, alias)) {
                        this.authorizationApi.enableIdentityProviderPermission(this.realm, alias);
                    }
                }
                default -> throw unsupportedType(type, authzName);
            }
            logger.debug("Permissions enabled for '{}' in realm '{}'", authzName, this.realm);
        } catch (final UnknownAuthorizationReferenceException unknown) {
            logger.warn(
                    "Unable to enable permissions for '{}'. Import will continue, but may fail later. Reason: {}",
                    authzName,
                    unknown.getMessage());
        } catch (final KeycloakAccessException error) {
            if (!ClientAuthorizationKeycloakGateway.isFgapV2Error(error)) {
                throw error;
            }
            logger.warn(
                    "Unable to enable permissions for '{}' - FGAP V2 active. Permissions managed at realm level.",
                    authzName);
        }
    }

    /** The name of a policy with its placeholder resolved. */
    String sanitizedPolicyName(final String authzName) {
        PermissionTypeAndId typeAndId = PermissionTypeAndId.fromPolicyName(authzName);
        if (typeAndId == null || !typeAndId.isPlaceholder()) {
            return authzName;
        }
        return authzName.replace(typeAndId.idOrPlaceholder, resolveObjectId(typeAndId, authzName));
    }

    /** The name of a resource with its placeholder resolved; the id alone with the permissions of version 2. */
    String sanitizedResourceName(final String authzName) {
        PermissionTypeAndId typeAndId = PermissionTypeAndId.fromResourceName(authzName);
        if (typeAndId == null || !typeAndId.isPlaceholder()) {
            return authzName;
        }
        String id = resolveObjectId(typeAndId, authzName);
        return this.fgapV2 ? id : authzName.replace(typeAndId.idOrPlaceholder, id);
    }

    private String resolveObjectId(final PermissionTypeAndId typeAndId, final String authzName) {
        if (!typeAndId.isPlaceholder()) {
            return typeAndId.idOrPlaceholder;
        }
        String placeholder = typeAndId.getPlaceholder();
        return switch (typeAndId.type) {
            case CLIENT ->
                this.clientApi
                        .findByClientId(this.realm, placeholder)
                        .map(ClientRepresentation::getId)
                        .orElseThrow(() ->
                                unknown("Cannot find client '%s' in realm '%s' for '%s'", placeholder, authzName));
            case GROUP ->
                this.groupApi
                        .findByPath(this.realm, placeholder)
                        .map(GroupRepresentation::getId)
                        .orElseThrow(() -> unknown(
                                "Cannot find group with path '%s' in realm '%s' for '%s'", placeholder, authzName));
            case ROLE ->
                this.roleApi
                        .findRealmRole(this.realm, placeholder)
                        .map(RoleRepresentation::getId)
                        .orElseThrow(() ->
                                unknown("Cannot find realm role '%s' in realm '%s' for '%s'", placeholder, authzName));
            case IDENTITY_PROVIDER ->
                identityProviders().stream()
                        .filter(idp -> Objects.equals(idp.getAlias(), placeholder))
                        .map(IdentityProviderRepresentation::getInternalId)
                        .findAny()
                        .orElseThrow(() -> unknown(
                                "Cannot find identity provider with alias '%s' in realm '%s' for '%s'",
                                placeholder, authzName));
            default -> throw unsupportedType(typeAndId.type, authzName);
        };
    }

    private String identityProviderAliasOf(final String internalId) {
        return identityProviders().stream()
                .filter(idp -> Objects.equals(idp.getInternalId(), internalId))
                .map(IdentityProviderRepresentation::getAlias)
                .findAny()
                .orElseThrow(() -> new UnknownAuthorizationReferenceException(String.format(
                        "Cannot find identity provider with internal id '%s' in realm '%s'", internalId, this.realm)));
    }

    private List<IdentityProviderRepresentation> identityProviders() {
        if (this.identityProviders == null) {
            this.identityProviders = this.identityProviderApi.listIdentityProviders(this.realm);
        }
        return this.identityProviders;
    }

    private UnknownAuthorizationReferenceException unknown(
            final String format, final String placeholder, final String authzName) {
        return new UnknownAuthorizationReferenceException(String.format(format, placeholder, this.realm, authzName));
    }

    private UnknownAuthorizationReferenceException unsupportedType(final String type, final String authzName) {
        return new UnknownAuthorizationReferenceException(String.format(
                "Cannot resolve '%s' in realm '%s', the type '%s' is not supported by keycloak-config-cli.",
                authzName, this.realm, type));
    }
}
