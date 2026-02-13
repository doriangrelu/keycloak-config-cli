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

package io.github.doriangrelu.keycloak.config.service;

import io.github.doriangrelu.keycloak.config.ThreadHelper;
import io.github.doriangrelu.keycloak.config.exception.ImportProcessingException;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.repository.GroupRepository;
import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import io.github.doriangrelu.keycloak.config.util.CloneUtil;
import io.github.doriangrelu.keycloak.config.util.KeycloakUtil;
import org.keycloak.representations.idm.GroupRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service responsible for importing and managing Keycloak groups.
 *
 * <p>This service handles the complete lifecycle of group management during realm import,
 * including creation, update, and deletion of groups and their hierarchical subgroups.
 * It supports both sequential and parallel processing modes for optimal performance.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Create or update top-level groups and nested subgroups</li>
 *   <li>Manage realm roles and client roles assigned to groups</li>
 *   <li>Recursive deletion of orphaned groups not present in import configuration</li>
 *   <li>Support for protected groups that are excluded from deletion</li>
 *   <li>Parallel processing support for improved performance</li>
 * </ul>
 *
 * <h2>Group Hierarchy Management:</h2>
 * <p>Groups in Keycloak can be organized in a tree structure. This service maintains
 * the full hierarchy by recursively processing subgroups. When deleting orphaned groups,
 * it traverses the entire tree to ensure consistency between the import configuration
 * and the actual Keycloak state.</p>
 *
 * @see GroupRepository
 * @see RealmImport
 * @see ExecutionContextHolder
 */
@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class GroupImportService {
    private static final Logger logger = LoggerFactory.getLogger(GroupImportService.class);

    /**
     * Maximum number of retry attempts when loading a newly created group.
     * This handles eventual consistency issues in parallel mode.
     */
    private static final int LOAD_CREATED_GROUP_MAX_RETRIES = 5;

    private final GroupRepository groupRepository;
    private final ImportConfigProperties importConfigProperties;
    private final ThreadHelper threadHelper;

    /**
     * Constructs a new GroupImportService with required dependencies.
     *
     * @param groupRepository        repository for group CRUD operations
     * @param importConfigProperties configuration properties for import behavior
     * @param threadHelper           helper for thread-related operations (sleep, etc.)
     */
    public GroupImportService(
            final GroupRepository groupRepository,
            final ImportConfigProperties importConfigProperties,
            final ThreadHelper threadHelper
    ) {
        this.groupRepository = groupRepository;
        this.importConfigProperties = importConfigProperties;
        this.threadHelper = threadHelper;
    }

    /**
     * Imports groups from a realm import configuration into Keycloak.
     *
     * <p>This method processes all groups defined in the realm import, creating new groups
     * or updating existing ones. The imported groups are stored in the execution context
     * for later reference (e.g., for orphan deletion).</p>
     *
     * @param realmImport the realm import configuration containing groups to import
     */
    public void importGroups(final RealmImport realmImport) {
        final List<GroupRepresentation> groups = realmImport.getGroups();
        final String realmName = realmImport.getRealm();

        if (groups == null) {
            return;
        }

        this.createOrUpdateGroups(groups, realmName);

        ExecutionContextHolder.context().put(realmImport.getRealm(), GroupRepresentation.class, groups);
    }

    /**
     * Creates or updates a list of groups in the specified realm.
     *
     * <p>Processing can be parallel or sequential based on the {@code parallel} configuration property.
     * Parallel mode improves performance but may require retry logic for newly created groups.</p>
     *
     * @param groups    the list of groups to create or update
     * @param realmName the name of the target realm
     */
    public void createOrUpdateGroups(final List<GroupRepresentation> groups, final String realmName) {
        final Consumer<GroupRepresentation> loop = group -> this.createOrUpdateRealmGroup(realmName, group);
        if (this.importConfigProperties.isParallel()) {
            groups.parallelStream().forEach(loop);
        } else {
            groups.forEach(loop);
        }
    }

    /**
     * Deletes groups that exist in Keycloak but are not present in the import configuration.
     *
     * <p>This method performs a recursive comparison between the imported group hierarchy
     * and the existing groups in Keycloak. Any group (or subgroup) that exists in Keycloak
     * but is not defined in the import will be deleted, unless it is marked as protected.</p>
     *
     * <p>The algorithm works as follows:</p>
     * <ol>
     *   <li>Build a path-based lookup map of all imported groups (including nested subgroups)</li>
     *   <li>For each existing top-level group in Keycloak:
     *     <ul>
     *       <li>If protected: skip deletion</li>
     *       <li>If present in import: recursively check and delete orphaned subgroups</li>
     *       <li>If not present in import: delete the entire group (and its subgroups)</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param realmImport the realm import configuration used as reference for deletion
     * @see KeycloakUtil#doesProtected(String, String)
     */
    public void deleteGroupsMissingInImport(final RealmImport realmImport) {
        final String realmName = realmImport.getRealm();
        final Collection<GroupRepresentation> importedGroups = ExecutionContextHolder.context().get(realmName, GroupRepresentation.class);

        final Map<String, GroupRepresentation> groupPathMap = importedGroups.stream()
                .flatMap(group -> this.flattenGroupHierarchy(group, "/"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.groupRepository.getAll(realmName).stream()
                .filter(group -> !KeycloakUtil.doesProtected(realmName, group.getName()))
                .forEach(existingGroup -> this.processGroupDeletion(groupPathMap, realmName, existingGroup));
    }

    /**
     * Processes the deletion logic for a single top-level group.
     *
     * <p>If the group exists in the import map, its subgroups are checked recursively.
     * Otherwise, the entire group is deleted.</p>
     *
     * @param groupPathMap map of imported group paths for lookup
     * @param realmName    the realm name
     * @param group        the group to process
     */
    private void processGroupDeletion(final Map<String, GroupRepresentation> groupPathMap, final String realmName, final GroupRepresentation group) {
        final String groupPath = "/" + group.getName();

        if (groupPathMap.containsKey(groupPath)) {
            this.deleteOrphanedSubGroupsRecursively(groupPathMap, realmName, group.getId());
        } else {
            this.doDeleteGroup(realmName, group);
        }
    }

    /**
     * Recursively traverses and deletes orphaned subgroups.
     *
     * <p>For each subgroup of the parent:</p>
     * <ul>
     *   <li>If the subgroup path exists in the import map: recurse into its children</li>
     *   <li>If the subgroup path is not in the import map: delete it</li>
     * </ul>
     *
     * @param groupPathMap  map of imported group paths for lookup
     * @param realmName     the realm name
     * @param parentGroupId the ID of the parent group whose subgroups are being checked
     */
    private void deleteOrphanedSubGroupsRecursively(final Map<String, GroupRepresentation> groupPathMap, final String realmName, final String parentGroupId) {
        this.groupRepository.getSubGroups(realmName, parentGroupId).forEach(subGroup -> {
            if (groupPathMap.containsKey(subGroup.getPath())) {
                this.deleteOrphanedSubGroupsRecursively(groupPathMap, realmName, subGroup.getId());
            } else {
                this.doDeleteGroup(realmName, subGroup);
            }
        });
    }

    private void doDeleteGroup(final String realmName, final GroupRepresentation group) {
        if (!KeycloakUtil.doesProtected(realmName, group.getName())) {
            logger.warn("Delete group '{}' in realm '{}'", group.getPath(), realmName);
            this.groupRepository.deleteGroup(realmName, group.getId());
        }
    }

    /**
     * Flattens a group hierarchy into a stream of path-to-group entries.
     *
     * <p>This method recursively traverses the group tree, producing entries where:</p>
     * <ul>
     *   <li>Key: the full path of the group (e.g., "/parent/child/grandchild")</li>
     *   <li>Value: the corresponding GroupRepresentation</li>
     * </ul>
     *
     * <p>Example: For a group "A" with subgroup "B" which has subgroup "C":</p>
     * <pre>
     * /A -> GroupRepresentation(A)
     * /A/B -> GroupRepresentation(B)
     * /A/B/C -> GroupRepresentation(C)
     * </pre>
     *
     * @param group  the group to flatten
     * @param prefix the path prefix (typically "/" for top-level groups)
     * @return a stream of map entries representing the flattened hierarchy
     */
    private Stream<Map.Entry<String, GroupRepresentation>> flattenGroupHierarchy(final GroupRepresentation group, final String prefix) {
        final String groupPath = prefix + group.getName();

        final Stream<Map.Entry<String, GroupRepresentation>> currentEntry = Stream.of(Map.entry(groupPath, group));

        return Optional.ofNullable(group.getSubGroups())
                .filter(subGroups -> !subGroups.isEmpty())
                .map(subGroups -> Stream.concat(
                        currentEntry,
                        subGroups.stream().flatMap(subGroup -> this.flattenGroupHierarchy(subGroup, groupPath + "/"))))
                .orElse(currentEntry);
    }

    /**
     * Creates or updates a single realm group based on its existence in Keycloak.
     *
     * @param realmName the realm name
     * @param group     the group to create or update
     */
    private void createOrUpdateRealmGroup(final String realmName, final GroupRepresentation group) {
        final String groupName = group.getName();

        final GroupRepresentation existingGroup = this.groupRepository.getGroupByName(realmName, group.getName());

        if (existingGroup != null) {
            this.updateGroupIfNecessary(realmName, group, existingGroup);
        } else {
            logger.debug("Create group '{}' in realm '{}'", groupName, realmName);
            this.createGroup(realmName, group);
        }
    }

    /**
     * Creates a new group in Keycloak with all its associated roles and subgroups.
     *
     * <p>After creating the group, this method:</p>
     * <ol>
     *   <li>Waits for the group to be available (with retry logic)</li>
     *   <li>Adds realm roles to the group</li>
     *   <li>Adds client roles to the group</li>
     *   <li>Recursively creates all subgroups</li>
     * </ol>
     *
     * @param realmName the realm name
     * @param group     the group to create
     */
    private void createGroup(final String realmName, final GroupRepresentation group) {
        this.groupRepository.createGroup(realmName, group);

        final GroupRepresentation existingGroup = this.loadCreatedGroupUsingRamp(realmName, group.getName(), 0);
        final GroupRepresentation patchedGroup = CloneUtil.patch(existingGroup, group);

        this.addRealmRoles(realmName, patchedGroup);
        this.addClientRoles(realmName, patchedGroup);
        this.addSubGroups(realmName, patchedGroup);
    }

    /**
     * Loads a newly created group with exponential backoff retry logic.
     *
     * <p>This method handles eventual consistency issues that occur in parallel mode,
     * where a newly created group may not be immediately available for retrieval.
     * The delay follows a quadratic progression: 0ms, 500ms, 2000ms, 4500ms, 8000ms.</p>
     *
     * @param realmName  the realm name
     * @param groupName  the name of the group to load
     * @param retryCount current retry attempt (starts at 0)
     * @return the loaded group representation
     * @throws ImportProcessingException if the group cannot be found after max retries
     */
    private GroupRepresentation loadCreatedGroupUsingRamp(final String realmName, final String groupName, final int retryCount) {
        if (retryCount >= LOAD_CREATED_GROUP_MAX_RETRIES) {
            throw new ImportProcessingException("Cannot find created group '%s' in realm '%s'", groupName, realmName);
        }

        final GroupRepresentation existingGroup = this.groupRepository.getGroupByName(realmName, groupName);

        if (existingGroup != null) {
            return existingGroup;
        }

        this.threadHelper.sleep(500L * retryCount * retryCount);

        return this.loadCreatedGroupUsingRamp(realmName, groupName, retryCount + 1);
    }

    /**
     * Assigns realm-level roles to a group.
     *
     * @param realmName     the realm name
     * @param existingGroup the group to which roles will be assigned
     */
    private void addRealmRoles(final String realmName, final GroupRepresentation existingGroup) {
        final List<String> realmRoles = existingGroup.getRealmRoles();

        if (realmRoles != null && !realmRoles.isEmpty()) {
            this.groupRepository.addRealmRoles(realmName, existingGroup.getId(), realmRoles);
        }
    }

    /**
     * Assigns client-level roles to a group.
     *
     * <p>Client roles are organized by client ID. This method iterates through all
     * client role mappings and assigns them to the group.</p>
     *
     * @param realmName     the realm name
     * @param existingGroup the group to which client roles will be assigned
     */
    private void addClientRoles(final String realmName, final GroupRepresentation existingGroup) {
        final Map<String, List<String>> existingClientRoles = existingGroup.getClientRoles();
        final String groupId = existingGroup.getId();

        if (existingClientRoles != null && !existingClientRoles.isEmpty()) {
            for (final Map.Entry<String, List<String>> existingClientRolesEntry : existingClientRoles.entrySet()) {
                final String clientId = existingClientRolesEntry.getKey();
                final List<String> clientRoleNames = existingClientRolesEntry.getValue();

                this.groupRepository.addClientRoles(realmName, groupId, clientId, clientRoleNames);
            }
        }
    }

    /**
     * Recursively adds all subgroups to a parent group.
     *
     * @param realmName     the realm name
     * @param existingGroup the parent group
     */
    private void addSubGroups(final String realmName, final GroupRepresentation existingGroup) {
        final List<GroupRepresentation> subGroups = existingGroup.getSubGroups();
        final String groupId = existingGroup.getId();

        if (subGroups != null && !subGroups.isEmpty()) {
            for (final GroupRepresentation subGroup : subGroups) {
                this.addSubGroup(realmName, groupId, subGroup);
            }
        }
    }

    /**
     * Adds a subgroup to a parent group with all its roles and nested subgroups.
     *
     * <p>This method performs a complete setup of the subgroup:</p>
     * <ol>
     *   <li>Creates the subgroup under the parent</li>
     *   <li>Retrieves the created subgroup from Keycloak</li>
     *   <li>Assigns realm roles</li>
     *   <li>Assigns client roles</li>
     *   <li>Recursively creates nested subgroups</li>
     * </ol>
     *
     * @param realmName     the realm name
     * @param parentGroupId the ID of the parent group
     * @param subGroup      the subgroup to add
     */
    public void addSubGroup(final String realmName, final String parentGroupId, final GroupRepresentation subGroup) {
        this.groupRepository.addSubGroup(realmName, parentGroupId, subGroup);

        final GroupRepresentation existingSubGroup = this.groupRepository.getSubGroupByName(realmName, parentGroupId, subGroup.getName());
        final GroupRepresentation patchedGroup = CloneUtil.patch(existingSubGroup, subGroup);

        this.addRealmRoles(realmName, patchedGroup);
        this.addClientRoles(realmName, patchedGroup);
        this.addSubGroups(realmName, patchedGroup);
    }

    /**
     * Updates a group only if there are actual changes to apply.
     *
     * <p>This method compares the existing group with the patched version and
     * skips the update if they are equal, avoiding unnecessary API calls.</p>
     *
     * @param realmName     the realm name
     * @param group         the imported group configuration
     * @param existingGroup the current group state in Keycloak
     */
    private void updateGroupIfNecessary(final String realmName, final GroupRepresentation group, final GroupRepresentation existingGroup) {
        final GroupRepresentation patchedGroup = CloneUtil.patch(existingGroup, group);
        final String groupName = existingGroup.getName();

        if (this.isGroupEqual(existingGroup, patchedGroup)) {
            logger.debug("No need to update group '{}' in realm '{}'", groupName, realmName);
        } else {
            logger.debug("Update group '{}' in realm '{}'", groupName, realmName);
            this.updateGroup(realmName, group, patchedGroup);
        }
    }

    private boolean isGroupEqual(final GroupRepresentation existingGroup, final GroupRepresentation patchedGroup) {
        if (!CloneUtil.deepEquals(existingGroup, patchedGroup, "subGroups")) {
            return false;
        }

        final List<GroupRepresentation> importedSubGroups = patchedGroup.getSubGroups();
        final List<GroupRepresentation> existingSubGroups = existingGroup.getSubGroups();

        if (importedSubGroups.isEmpty() && existingSubGroups.isEmpty()) {
            return true;
        }

        if (importedSubGroups.size() != existingSubGroups.size()) {
            return false;
        }

        return this.areSubGroupsEqual(existingSubGroups, importedSubGroups);
    }

    private boolean areSubGroupsEqual(final List<GroupRepresentation> existingSubGroups, final List<GroupRepresentation> importedSubGroups) {
        for (final GroupRepresentation importedSubGroup : importedSubGroups) {
            final GroupRepresentation existingSubGroup = existingSubGroups.stream()
                    .filter(group -> Objects.equals(group.getName(), importedSubGroup.getName()))
                    .findFirst().orElse(null);

            if (existingSubGroup == null) {
                return false;
            }

            final GroupRepresentation patchedSubGroup = CloneUtil.patch(existingSubGroup, importedSubGroup);

            if (!CloneUtil.deepEquals(existingSubGroup, patchedSubGroup, "id")) {
                return false;
            }
        }

        return true;
    }

    private void updateGroup(final String realmName, final GroupRepresentation group, final GroupRepresentation patchedGroup) {
        this.groupRepository.update(realmName, patchedGroup);

        final String groupId = patchedGroup.getId();

        final List<String> realmRoles = group.getRealmRoles();
        if (realmRoles != null) {
            this.updateGroupRealmRoles(realmName, groupId, realmRoles);
        }

        final Map<String, List<String>> clientRoles = group.getClientRoles();
        if (clientRoles != null) {
            this.updateGroupClientRoles(realmName, groupId, clientRoles);
        }

        final List<GroupRepresentation> subGroups = group.getSubGroups();
        if (subGroups != null) {
            this.updateSubGroups(realmName, patchedGroup.getId(), subGroups);
        }
    }

    private void updateGroupRealmRoles(final String realmName, final String groupId, final List<String> realmRoles) {
        final GroupRepresentation existingGroup = this.groupRepository.getGroupById(realmName, groupId);

        final List<String> existingRealmRolesNames = existingGroup.getRealmRoles();

        final List<String> realmRoleNamesToAdd = this.estimateRealmRolesToAdd(realmRoles, existingRealmRolesNames);
        final List<String> realmRoleNamesToRemove = this.estimateRealmRolesToRemove(realmRoles, existingRealmRolesNames);

        this.groupRepository.addRealmRoles(realmName, groupId, realmRoleNamesToAdd);
        this.groupRepository.removeRealmRoles(realmName, groupId, realmRoleNamesToRemove);
    }

    private List<String> estimateRealmRolesToRemove(final List<String> realmRoles, final List<String> existingRealmRolesNames) {
        if (existingRealmRolesNames == null) {
            return Collections.emptyList();
        }

        final List<String> realmRoleNamesToRemove = new ArrayList<>();

        for (final String existingRealmRolesName : existingRealmRolesNames) {
            if (!realmRoles.contains(existingRealmRolesName)) {
                realmRoleNamesToRemove.add(existingRealmRolesName);
            }
        }

        return realmRoleNamesToRemove;
    }

    private List<String> estimateRealmRolesToAdd(final List<String> realmRoles, final List<String> existingRealmRolesNames) {
        if (existingRealmRolesNames == null) {
            return realmRoles;
        }

        final List<String> realmRoleNamesToAdd = new ArrayList<>();

        for (final String realmRoleName : realmRoles) {
            if (!existingRealmRolesNames.contains(realmRoleName)) {
                realmRoleNamesToAdd.add(realmRoleName);
            }
        }

        return realmRoleNamesToAdd;
    }

    private void updateGroupClientRoles(final String realmName, final String groupId, final Map<String, List<String>> groupClientRoles) {
        final GroupRepresentation existingGroup = this.groupRepository.getGroupById(realmName, groupId);

        final Map<String, List<String>> existingClientRoleNames = existingGroup.getClientRoles();

        this.deleteClientRolesMissingInImport(realmName, groupId, existingClientRoleNames, groupClientRoles);
        this.updateClientRoles(realmName, groupId, existingClientRoleNames, groupClientRoles);
    }

    private void updateClientRoles(
            final String realmName,
            final String groupId,
            final Map<String, List<String>> existingClientRoleNames,
            final Map<String, List<String>> groupClientRoles
    ) {
        for (final Map.Entry<String, List<String>> clientRole : groupClientRoles.entrySet()) {
            final String clientId = clientRole.getKey();
            final List<String> clientRoleNames = clientRole.getValue();

            final List<String> existingClientRoleNamesForClient =
                    existingClientRoleNames == null ? null : existingClientRoleNames.get(clientId);

            final List<String> clientRoleNamesToAdd = this.estimateClientRolesToAdd(existingClientRoleNamesForClient, clientRoleNames);
            final List<String> clientRoleNamesToRemove = this.estimateClientRolesToRemove(existingClientRoleNamesForClient, clientRoleNames);

            this.groupRepository.addClientRoles(realmName, groupId, clientId, clientRoleNamesToAdd);
            this.groupRepository.removeClientRoles(realmName, groupId, clientId, clientRoleNamesToRemove);
        }
    }

    private void deleteClientRolesMissingInImport(
            final String realmName,
            final String groupId,
            final Map<String, List<String>> existingClientRoleNames,
            final Map<String, List<String>> groupClientRoles
    ) {
        if (CollectionUtils.isEmpty(existingClientRoleNames)) {
            return;
        }

        for (final Map.Entry<String, List<String>> existingClientRoleNamesEntry : existingClientRoleNames.entrySet()) {
            final String clientId = existingClientRoleNamesEntry.getKey();
            final List<String> clientRoleNames = existingClientRoleNamesEntry.getValue();

            if (!clientRoleNames.isEmpty() && !groupClientRoles.containsKey(clientId)) {
                this.groupRepository.removeClientRoles(realmName, groupId, clientId, clientRoleNames);
            }
        }
    }

    private List<String> estimateClientRolesToRemove(final List<String> existingClientRoleNamesForClient, final List<String> clientRoleNamesFromImport) {
        if (CollectionUtils.isEmpty(existingClientRoleNamesForClient)) {
            return Collections.emptyList();
        }

        final List<String> clientRoleNamesToRemove = new ArrayList<>();

        for (final String existingClientRoleNameForClient : existingClientRoleNamesForClient) {
            if (!clientRoleNamesFromImport.contains(existingClientRoleNameForClient)) {
                clientRoleNamesToRemove.add(existingClientRoleNameForClient);
            }
        }

        return clientRoleNamesToRemove;
    }

    private List<String> estimateClientRolesToAdd(final List<String> existingClientRoleNamesForClient, final List<String> clientRoleNamesFromImport) {
        if (CollectionUtils.isEmpty(existingClientRoleNamesForClient)) {
            return clientRoleNamesFromImport;
        }

        final List<String> clientRoleNamesToAdd = new ArrayList<>();

        for (final String clientRoleName : clientRoleNamesFromImport) {
            if (!existingClientRoleNamesForClient.contains(clientRoleName)) {
                clientRoleNamesToAdd.add(clientRoleName);
            }
        }

        return clientRoleNamesToAdd;
    }

    private void updateSubGroups(final String realmName, final String parentGroupId, final List<GroupRepresentation> subGroups) {
        final List<GroupRepresentation> existingSubGroups = this.groupRepository.getSubGroups(realmName, parentGroupId);

        this.deleteAllSubGroupsMissingInImport(realmName, subGroups, existingSubGroups);

        final Set<String> existingSubGroupNames = existingSubGroups.stream()
                .map(GroupRepresentation::getName)
                .collect(Collectors.toSet());

        for (final GroupRepresentation subGroup : subGroups) {
            if (existingSubGroupNames.contains(subGroup.getName())) {
                this.updateSubGroupIfNecessary(realmName, parentGroupId, subGroup);
            } else {
                this.addSubGroup(realmName, parentGroupId, subGroup);
            }
        }
    }

    private void deleteAllSubGroupsMissingInImport(
            final String realmName,
            final List<GroupRepresentation> importedSubGroups,
            final List<GroupRepresentation> existingSubGroups
    ) {
        final Set<String> importedSubGroupNames = importedSubGroups.stream()
                .map(GroupRepresentation::getName)
                .collect(Collectors.toSet());

        for (final GroupRepresentation existingSubGroup : existingSubGroups) {
            if (importedSubGroupNames.contains(existingSubGroup.getName())) {
                continue;
            }

            this.groupRepository.deleteGroup(realmName, existingSubGroup.getId());
        }
    }

    public void updateSubGroupIfNecessary(final String realmName, final String parentGroupId, final GroupRepresentation subGroup) {
        final String subGroupName = subGroup.getName();
        final GroupRepresentation existingSubGroup = this.groupRepository.getSubGroupByName(realmName, parentGroupId, subGroupName);

        final GroupRepresentation patchedSubGroup = CloneUtil.patch(existingSubGroup, subGroup);

        if (CloneUtil.deepEquals(existingSubGroup, patchedSubGroup)) {
            logger.debug("No need to update subGroup '{}' in group with id '{}' in realm '{}'", subGroupName, parentGroupId, realmName);
        } else {
            logger.debug("Update subGroup '{}' in group with id '{}' in realm '{}'", subGroupName, parentGroupId, realmName);

            this.updateGroup(realmName, subGroup, patchedSubGroup);
        }
    }
}
