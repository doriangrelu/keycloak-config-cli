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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api;

import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;

/**
 * Authorization services and fine-grained admin permissions of the Keycloak Admin API (ADR-003). A client is
 * addressed by its id; resources, scopes and policies by their name.
 */
public interface ClientAuthorizationApi {

    /** Whether the fine-grained admin permissions of the server are the realm-level ones of version 2. */
    boolean isFgapV2Active();

    ResourceServerRepresentation exportSettings(String realm, String clientUuid);

    void updateSettings(String realm, String clientUuid, ResourceServerRepresentation settings);

    void createResource(String realm, String clientUuid, ResourceRepresentation resource);

    void updateResource(String realm, String clientUuid, ResourceRepresentation resource);

    void deleteResource(String realm, String clientUuid, String name);

    void createScope(String realm, String clientUuid, ScopeRepresentation scope);

    void updateScope(String realm, String clientUuid, ScopeRepresentation scope);

    void deleteScope(String realm, String clientUuid, String name);

    void createPolicy(String realm, String clientUuid, PolicyRepresentation policy);

    void updatePolicy(String realm, String clientUuid, PolicyRepresentation policy);

    void deletePolicy(String realm, String clientUuid, String name);

    boolean isClientPermissionEnabled(String realm, String clientUuid);

    void enableClientPermission(String realm, String clientUuid);

    boolean isGroupPermissionEnabled(String realm, String groupId);

    void enableGroupPermission(String realm, String groupId);

    boolean isRealmRolePermissionEnabled(String realm, String roleId);

    void enableRealmRolePermission(String realm, String roleId);

    boolean isIdentityProviderPermissionEnabled(String realm, String alias);

    void enableIdentityProviderPermission(String realm, String alias);
}
