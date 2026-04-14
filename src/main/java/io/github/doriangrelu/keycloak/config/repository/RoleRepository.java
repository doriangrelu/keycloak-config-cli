/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
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

package io.github.doriangrelu.keycloak.config.repository;

import io.github.doriangrelu.keycloak.config.exception.ImportProcessingException;
import io.github.doriangrelu.keycloak.config.exception.KeycloakRepositoryException;
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import io.github.doriangrelu.keycloak.config.resource.ManagementPermissions;
import io.github.doriangrelu.keycloak.config.util.KeycloakUtil;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ManagementPermissionRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RoleRepository {

    private static final Logger log = LoggerFactory.getLogger(RoleRepository.class);

    private final RealmRepository realmRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final KeycloakProvider keycloakProvider;

    @Autowired
    public RoleRepository(
            final RealmRepository realmRepository,
            final ClientRepository clientRepository,
            final UserRepository userRepository,
            final KeycloakProvider keycloakProvider) {
        this.realmRepository = realmRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.keycloakProvider = keycloakProvider;
    }


    public Optional<RoleRepresentation> searchRealmRole(final String realmName, final String name) {
        Optional<RoleRepresentation> maybeRole;

        final RolesResource rolesResource = this.realmRepository.getResource(realmName).roles();
        final RoleResource roleResource = rolesResource.get(name);

        try {
            maybeRole = Optional.of(roleResource.toRepresentation());
        } catch (final NotFoundException e) {
            maybeRole = Optional.empty();
        }

        return maybeRole;
    }

    public void createRealmRole(final String realmName, final RoleRepresentation role) {
        final RolesResource rolesResource = this.realmRepository.getResource(realmName).roles();
        try {
            rolesResource.create(role);
        } catch (final Exception e) {
            throw new KeycloakRepositoryException(
                    "Cannot create realm role '%s' within realm '%s': %s",
                    role.getName(), realmName, e.getMessage()
            );
        }
    }

    public void updateRealmRole(final String realmName, final RoleRepresentation roleToUpdate) {
        final RoleResource roleResource = this.realmRepository.getResource(realmName)
                .roles()
                .get(roleToUpdate.getName());

        roleResource.update(roleToUpdate);
    }

    public void deleteRealmRole(final String realmName, final RoleRepresentation roleToUpdate) {
        if (!KeycloakUtil.isDefaultRole(roleToUpdate) && !KeycloakUtil.doesProtected(realmName, roleToUpdate.getName())) {
            log.warn("Delete role '{}' for realm '{}'", roleToUpdate.getName(), realmName);
            this.realmRepository.getResource(realmName)
                    .roles()
                    .deleteRole(roleToUpdate.getName());
        } else {
            log.debug("Keep role '{}' for realm '{}'", roleToUpdate.getName(), realmName);
        }
    }

    public RoleRepresentation getRealmRole(final String realmName, final String roleName) {
        return this.searchRealmRole(realmName, roleName)
                .orElseThrow(() -> new KeycloakRepositoryException(
                        "Cannot find realm role '%s' within realm '%s'", roleName, realmName
                ));
    }

    public List<RoleRepresentation> getRealmRoles(final String realmName) {
        return this.realmRepository.getResource(realmName)
                .roles().list();
    }

    public List<RoleRepresentation> getRealmRolesByName(final String realmName, final Collection<String> roles) {
        return roles.stream()
                .map(role -> this.getRealmRole(realmName, role))
                .toList();
    }

    public final RoleRepresentation getClientRole(final String realmName, final String clientId, final String roleName) {
        final ClientRepresentation client = this.clientRepository.getByClientId(realmName, clientId);
        final RealmResource realmResource = this.realmRepository.getResource(realmName);

        final List<RoleRepresentation> clientRoles = realmResource.clients()
                .get(client.getId())
                .roles()
                .list();

        return clientRoles.stream()
                .filter(r -> Objects.equals(r.getName(), roleName))
                .findFirst()
                .orElse(null);
    }

    public Map<String, List<RoleRepresentation>> getClientRoles(final String realmName) {
        return this.realmRepository.getResource(realmName).clients().findAll().stream()
                .collect(Collectors.toMap(
                        ClientRepresentation::getClientId,
                        client -> this.realmRepository.getResource(realmName).clients()
                                .get(client.getId()).roles().list()
                ));
    }

    public List<RoleRepresentation> getClientRolesByName(final String realmName, final String clientId, final List<String> roleNames) {
        final ClientResource clientResource = this.clientRepository.getResourceByClientId(realmName, clientId);

        final List<RoleRepresentation> roles = new ArrayList<>();

        for (final String roleName : roleNames) {
            try {
                roles.add(clientResource.roles().get(roleName).toRepresentation());
            } catch (final NotFoundException e) {
                throw new KeycloakRepositoryException(
                        "Cannot find client role '%s' for client '%s' within realm '%s'",
                        roleName, clientId, realmName
                );
            }
        }

        return roles;
    }

    public void createClientRole(final String realmName, final String clientId, final RoleRepresentation role) {
        final RolesResource rolesResource = this.clientRepository.getResourceByClientId(realmName, clientId).roles();
        rolesResource.create(role);

        // KEYCLOAK-16082
        this.updateClientRole(realmName, clientId, role);
    }

    public void updateClientRole(final String realmName, final String clientId, final RoleRepresentation role) {
        final RoleResource roleResource = this.loadClientRole(realmName, clientId, role.getName());
        roleResource.update(role);
    }

    public void deleteClientRole(final String realmName, final String clientId, final RoleRepresentation role) {
        final boolean doesProtectedClient = List.of("realm-management", "account", "broker", "master-realm").contains(clientId);
        final boolean doesProtectedResource = KeycloakUtil.doesProtected(realmName, role.getName());
        if (!doesProtectedClient && !doesProtectedResource) {
            log.warn("Delete client role '{}' within realm '{}' for client '{}'", role.getName(), realmName, clientId);
            final ClientRepresentation client = this.clientRepository.getByClientId(realmName, clientId);
            this.realmRepository.getResource(realmName)
                    .clients()
                    .get(client.getId())
                    .roles()
                    .deleteRole(role.getName());
        } else {
            log.debug("Keep client role '{}' for client '{}' within realm '{}' (protectedClient={}, protectedResource={})", role.getName(), clientId, realmName, doesProtectedClient, doesProtectedResource);
        }
    }

    public List<RoleRepresentation> searchRealmRoles(final String realmName, final List<String> roleNames) {
        final List<RoleRepresentation> roles = new ArrayList<>();
        final RealmResource realmResource = this.realmRepository.getResource(realmName);

        for (final String roleName : roleNames) {
            try {
                final RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();

                roles.add(role);
            } catch (final NotFoundException e) {
                throw new ImportProcessingException(
                        String.format("Could not find role '%s' in realm '%s'!", roleName, realmName)
                );
            }
        }

        return roles;
    }

    public List<String> getUserRealmLevelRoles(final String realmName, final String username) {
        final UserRepresentation user = this.userRepository.get(realmName, username);
        final UserResource userResource = this.realmRepository.getResource(realmName)
                .users()
                .get(user.getId());

        final List<RoleRepresentation> roles = userResource.roles()
                .realmLevel()
                .listAll();

        return roles.stream()
                .map(RoleRepresentation::getName)
                .toList();
    }

    public void addRealmRolesToUser(final String realmName, final String username, final List<RoleRepresentation> realmRoles) {
        final UserResource userResource = this.userRepository.getResource(realmName, username);
        userResource.roles().realmLevel().add(realmRoles);
    }

    public void removeRealmRolesForUser(final String realmName, final String username, final List<RoleRepresentation> realmRoles) {
        final UserResource userResource = this.userRepository.getResource(realmName, username);
        userResource.roles().realmLevel().remove(realmRoles);
    }

    public void addClientRolesToUser(final String realmName, final String username, final String clientId, final List<RoleRepresentation> clientRoles) {
        final ClientRepresentation client = this.clientRepository.getByClientId(realmName, clientId);
        final UserResource userResource = this.userRepository.getResource(realmName, username);

        final RoleScopeResource userClientRoles = userResource.roles()
                .clientLevel(client.getId());

        userClientRoles.add(clientRoles);
    }

    public void removeClientRolesForUser(final String realmName, final String username, final String clientId, final List<RoleRepresentation> clientRoles) {
        final ClientRepresentation client = this.clientRepository.getByClientId(realmName, clientId);
        final UserResource userResource = this.userRepository.getResource(realmName, username);

        final RoleScopeResource userClientRoles = userResource.roles()
                .clientLevel(client.getId());

        userClientRoles.remove(clientRoles);
    }

    public Map<String, List<String>> getUserClientLevelRoles(final String realmName, final String username) {
        final UserResource userResource = this.userRepository.getResource(realmName, username);

        final MappingsRepresentation mappings = userResource.roles()
                .getAll();

        return Optional.ofNullable(mappings.getClientMappings())
                .map(Map::entrySet)
                .orElseGet(Collections::emptySet)
                .stream()
                .map(entry -> new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(), this.toRoleNameList(entry.getValue().getMappings())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean isPermissionEnabled(final String realmName, final String id) {
        final ManagementPermissions permissions = this.keycloakProvider.getCustomApiProxy(ManagementPermissions.class);
        return permissions.getRealmRolePermissions(realmName, id).isEnabled();
    }

    public void enablePermission(final String realmName, final String id) {
        final ManagementPermissions permissions = this.keycloakProvider.getCustomApiProxy(ManagementPermissions.class);
        permissions.setRealmRolePermissions(realmName, id, new ManagementPermissionRepresentation(true));
    }

    private List<String> toRoleNameList(@Nullable final Collection<? extends RoleRepresentation> roles) {
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream().map(RoleRepresentation::getName).toList();
    }

    final RoleResource loadRealmRole(final String realmName, final String roleName) {
        final RealmResource realmResource = this.realmRepository.getResource(realmName);
        return realmResource
                .roles()
                .get(roleName);
    }

    final RoleResource loadClientRole(final String realmName, final String roleClientId, final String roleName) {
        return this.clientRepository.getResourceByClientId(realmName, roleClientId)
                .roles()
                .get(roleName);
    }
}
