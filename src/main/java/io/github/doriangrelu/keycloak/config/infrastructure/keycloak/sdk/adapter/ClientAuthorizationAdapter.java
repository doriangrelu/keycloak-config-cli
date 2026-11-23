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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.ManagementPermissions;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientAuthorizationApi;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.representations.idm.ManagementPermissionRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;

/** {@link ClientAuthorizationApi} on the Keycloak Admin client. */
public class ClientAuthorizationAdapter extends AbstractAdapter implements ClientAuthorizationApi {

    private final Supplier<ManagementPermissions> permissions;
    private final BooleanSupplier fgapV2Active;

    /**
     * @param permissions supplies the routes of the fine-grained permissions the Keycloak client lacks
     * @param fgapV2Active tells whether the server uses the fine-grained admin permissions of version 2
     */
    public ClientAuthorizationAdapter(
            final Supplier<Keycloak> keycloak,
            final Supplier<ManagementPermissions> permissions,
            final BooleanSupplier fgapV2Active) {
        super(keycloak);
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.fgapV2Active = Objects.requireNonNull(fgapV2Active, "fgapV2Active");
    }

    private ClientResource client(final String realm, final String clientUuid) {
        return keycloak().realm(realm).clients().get(clientUuid);
    }

    private AuthorizationResource authorization(final String realm, final String clientUuid) {
        return client(realm, clientUuid).authorization();
    }

    @Override
    public boolean isFgapV2Active() {
        return this.fgapV2Active.getAsBoolean();
    }

    @Override
    public ResourceServerRepresentation exportSettings(final String realm, final String clientUuid) {
        return execute(() -> authorization(realm, clientUuid).exportSettings());
    }

    @Override
    public void updateSettings(
            final String realm, final String clientUuid, final ResourceServerRepresentation settings) {
        execute(() -> authorization(realm, clientUuid).update(settings));
    }

    @Override
    public void createResource(final String realm, final String clientUuid, final ResourceRepresentation resource) {
        createWithoutId(() -> authorization(realm, clientUuid).resources().create(resource));
    }

    @Override
    public void updateResource(final String realm, final String clientUuid, final ResourceRepresentation resource) {
        execute(() -> resourceId(realm, clientUuid, resource.getName())
                .ifPresent(id -> authorization(realm, clientUuid)
                        .resources()
                        .resource(id)
                        .update(resource)));
    }

    @Override
    public void deleteResource(final String realm, final String clientUuid, final String name) {
        execute(() -> resourceId(realm, clientUuid, name)
                .ifPresent(id -> authorization(realm, clientUuid)
                        .resources()
                        .resource(id)
                        .remove()));
    }

    /**
     * The search by name of Keycloak is not exact, and also returns resources of other owners: a resource owned by the
     * client wins, then any resource with the exact name.
     */
    private Optional<String> resourceId(final String realm, final String clientUuid, final String name) {
        String clientId = client(realm, clientUuid).toRepresentation().getClientId();
        AuthorizationResource authorization = authorization(realm, clientUuid);
        Optional<String> owned = authorization.resources().findByName(name, clientId).stream()
                .filter(resource -> name.equals(resource.getName()))
                .findFirst()
                .map(ResourceRepresentation::getId);
        if (owned.isPresent()) {
            return owned;
        }
        return authorization.resources().findByName(name).stream()
                .filter(resource -> name.equals(resource.getName())
                        && (resource.getOwner() == null
                                || !clientId.equals(resource.getOwner().getName())))
                .findFirst()
                .map(ResourceRepresentation::getId);
    }

    @Override
    public void createScope(final String realm, final String clientUuid, final ScopeRepresentation scope) {
        createWithoutId(() -> authorization(realm, clientUuid).scopes().create(scope));
    }

    @Override
    public void updateScope(final String realm, final String clientUuid, final ScopeRepresentation scope) {
        execute(() -> Optional.ofNullable(
                        authorization(realm, clientUuid).scopes().findByName(scope.getName()))
                .ifPresent(existing -> authorization(realm, clientUuid)
                        .scopes()
                        .scope(existing.getId())
                        .update(scope)));
    }

    @Override
    public void deleteScope(final String realm, final String clientUuid, final String name) {
        execute(() -> Optional.ofNullable(
                        authorization(realm, clientUuid).scopes().findByName(name))
                .ifPresent(existing -> authorization(realm, clientUuid)
                        .scopes()
                        .scope(existing.getId())
                        .remove()));
    }

    @Override
    public void createPolicy(final String realm, final String clientUuid, final PolicyRepresentation policy) {
        createWithoutId(() -> authorization(realm, clientUuid).policies().create(policy));
    }

    @Override
    public void updatePolicy(final String realm, final String clientUuid, final PolicyRepresentation policy) {
        execute(() -> Optional.ofNullable(
                        authorization(realm, clientUuid).policies().findByName(policy.getName()))
                .ifPresent(existing -> authorization(realm, clientUuid)
                        .policies()
                        .policy(existing.getId())
                        .update(policy)));
    }

    @Override
    public void deletePolicy(final String realm, final String clientUuid, final String name) {
        execute(() -> Optional.ofNullable(
                        authorization(realm, clientUuid).policies().findByName(name))
                .ifPresent(existing -> authorization(realm, clientUuid)
                        .policies()
                        .policy(existing.getId())
                        .remove()));
    }

    @Override
    public boolean isClientPermissionEnabled(final String realm, final String clientUuid) {
        return execute(() -> client(realm, clientUuid).getPermissions().isEnabled());
    }

    @Override
    public void enableClientPermission(final String realm, final String clientUuid) {
        execute(() -> client(realm, clientUuid).setPermissions(new ManagementPermissionRepresentation(true)));
    }

    @Override
    public boolean isGroupPermissionEnabled(final String realm, final String groupId) {
        return execute(() ->
                keycloak().realm(realm).groups().group(groupId).getPermissions().isEnabled());
    }

    @Override
    public void enableGroupPermission(final String realm, final String groupId) {
        execute(() -> keycloak()
                .realm(realm)
                .groups()
                .group(groupId)
                .setPermissions(new ManagementPermissionRepresentation(true)));
    }

    @Override
    public boolean isRealmRolePermissionEnabled(final String realm, final String roleId) {
        return execute(() ->
                this.permissions.get().getRealmRolePermissions(realm, roleId).isEnabled());
    }

    @Override
    public void enableRealmRolePermission(final String realm, final String roleId) {
        execute(() -> this.permissions
                .get()
                .setRealmRolePermissions(realm, roleId, new ManagementPermissionRepresentation(true)));
    }

    @Override
    public boolean isIdentityProviderPermissionEnabled(final String realm, final String alias) {
        return execute(
                () -> this.permissions.get().getIdpPermissions(realm, alias).isEnabled());
    }

    @Override
    public void enableIdentityProviderPermission(final String realm, final String alias) {
        execute(() ->
                this.permissions.get().setIdpPermissions(realm, alias, new ManagementPermissionRepresentation(true)));
    }
}
