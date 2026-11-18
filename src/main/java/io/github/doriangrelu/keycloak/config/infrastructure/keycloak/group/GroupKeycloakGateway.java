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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group;

import io.github.doriangrelu.keycloak.config.application.group.UnknownGroupRoleException;
import io.github.doriangrelu.keycloak.config.application.group.port.GroupGateway;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link GroupGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class GroupKeycloakGateway implements GroupGateway {

    private final GroupApi groupApi;
    private final RoleApi roleApi;
    private final GroupKeycloakMapper mapper;

    public GroupKeycloakGateway(final GroupApi groupApi, final RoleApi roleApi, final GroupKeycloakMapper mapper) {
        this.groupApi = groupApi;
        this.roleApi = roleApi;
        this.mapper = mapper;
    }

    @Override
    public List<Group> findAll(final String realm) {
        List<Group> groups = new ArrayList<>();
        this.groupApi.listTopLevelGroups(realm).forEach(group -> collect(realm, group.getId(), groups));
        return groups;
    }

    /** Adds the group, then its descendants: parents always come first. */
    private void collect(final String realm, final String groupId, final List<Group> groups) {
        GroupRepresentation group = this.groupApi.getGroup(realm, groupId);
        List<GroupRepresentation> children = this.groupApi.listSubGroups(realm, groupId);
        if (group.getAttributes() == null) {
            group.setAttributes(Map.of());
        }
        if (group.getRealmRoles() == null) {
            group.setRealmRoles(List.of());
        }
        if (group.getClientRoles() == null) {
            group.setClientRoles(Map.of());
        }
        Set<String> childNames =
                children.stream().map(GroupRepresentation::getName).collect(Collectors.toSet());
        groups.add(this.mapper.toGroup(group, group.getPath(), childNames));
        children.forEach(child -> collect(realm, child.getId(), groups));
    }

    @Override
    public void create(final String realm, final Group group) {
        GroupRepresentation representation = this.mapper.toRepresentation(group);
        String groupId = group.parentPath() == null
                ? this.groupApi.createTopLevelGroup(realm, representation)
                : this.groupApi.createSubGroup(realm, idOf(realm, group.parentPath()), representation);
        updateRoleMappings(realm, groupId, group.realmRoles(), Set.of(), group.clientRoles(), Map.of());
    }

    @Override
    public void update(final String realm, final Group current, final Group target) {
        String groupId = idOf(realm, target.path());
        if (!Objects.equals(current.withoutRolesAndChildren(), target.withoutRolesAndChildren())) {
            GroupRepresentation representation = this.mapper.toRepresentation(target);
            representation.setId(groupId);
            this.groupApi.updateGroup(realm, groupId, representation);
        }
        updateRoleMappings(
                realm, groupId, target.realmRoles(), current.realmRoles(), target.clientRoles(), current.clientRoles());
    }

    @Override
    public void delete(final String realm, final String path) {
        this.groupApi.findByPath(realm, path).ifPresent(group -> this.groupApi.deleteGroup(realm, group.getId()));
    }

    /** Role mappings not specified by {@code target} ({@code null}) are left untouched. */
    private void updateRoleMappings(
            final String realm,
            final String groupId,
            final Set<String> targetRealmRoles,
            final Set<String> currentRealmRoles,
            final Map<String, Set<String>> targetClientRoles,
            final Map<String, Set<String>> currentClientRoles) {
        if (targetRealmRoles != null) {
            Set<String> current = currentRealmRoles == null ? Set.of() : currentRealmRoles;
            List<RoleRepresentation> removed = minus(current, targetRealmRoles).stream()
                    .map(name -> this.roleApi.findRealmRole(realm, name))
                    .flatMap(Optional::stream)
                    .toList();
            if (!removed.isEmpty()) {
                this.groupApi.removeRealmRoles(realm, groupId, removed);
            }
            List<RoleRepresentation> added = realmRoles(realm, minus(targetRealmRoles, current));
            if (!added.isEmpty()) {
                this.groupApi.addRealmRoles(realm, groupId, added);
            }
        }
        if (targetClientRoles != null) {
            Map<String, Set<String>> current = currentClientRoles == null ? Map.of() : currentClientRoles;
            Set<String> clients = new LinkedHashSet<>(current.keySet());
            clients.addAll(targetClientRoles.keySet());
            for (String clientId : clients) {
                Set<String> currentRoles = current.getOrDefault(clientId, Set.of());
                Set<String> targetRoles = targetClientRoles.getOrDefault(clientId, Set.of());
                updateClientRoleMappings(realm, groupId, clientId, currentRoles, targetRoles);
            }
        }
    }

    private void updateClientRoleMappings(
            final String realm,
            final String groupId,
            final String clientId,
            final Set<String> currentRoles,
            final Set<String> targetRoles) {
        if (currentRoles.equals(targetRoles)) {
            return;
        }
        String clientUuid = this.roleApi
                .findClientUuid(realm, clientId)
                .orElseThrow(() -> UnknownGroupRoleException.clientRole(
                        realm, clientId, targetRoles.stream().findFirst().orElse("")));
        List<RoleRepresentation> removed = minus(currentRoles, targetRoles).stream()
                .map(name -> this.roleApi.findClientRole(realm, clientUuid, name))
                .flatMap(Optional::stream)
                .toList();
        if (!removed.isEmpty()) {
            this.groupApi.removeClientRoles(realm, groupId, clientUuid, removed);
        }
        List<RoleRepresentation> added = clientRoles(realm, clientId, clientUuid, minus(targetRoles, currentRoles));
        if (!added.isEmpty()) {
            this.groupApi.addClientRoles(realm, groupId, clientUuid, added);
        }
    }

    private List<RoleRepresentation> realmRoles(final String realm, final Set<String> names) {
        return names.stream()
                .map(name -> this.roleApi
                        .findRealmRole(realm, name)
                        .orElseThrow(() -> UnknownGroupRoleException.realmRole(realm, name)))
                .toList();
    }

    private List<RoleRepresentation> clientRoles(
            final String realm, final String clientId, final String clientUuid, final Set<String> names) {
        return names.stream()
                .map(name -> this.roleApi
                        .findClientRole(realm, clientUuid, name)
                        .orElseThrow(() -> UnknownGroupRoleException.clientRole(realm, clientId, name)))
                .toList();
    }

    private String idOf(final String realm, final String path) {
        return this.groupApi
                .findByPath(realm, path)
                .map(GroupRepresentation::getId)
                .orElseThrow(() -> new IllegalStateException("Group '" + path + "' not found in realm " + realm));
    }

    private static Set<String> minus(final Set<String> values, final Set<String> removed) {
        Set<String> result = new LinkedHashSet<>(values);
        result.removeAll(removed);
        return result;
    }
}
