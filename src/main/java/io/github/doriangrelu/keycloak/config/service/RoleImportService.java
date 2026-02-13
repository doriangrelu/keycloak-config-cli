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

import io.github.doriangrelu.keycloak.config.exception.ImportProcessingException;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.repository.ClientRepository;
import io.github.doriangrelu.keycloak.config.repository.RoleRepository;
import io.github.doriangrelu.keycloak.config.service.rolecomposites.client.ClientRoleCompositeImportService;
import io.github.doriangrelu.keycloak.config.service.rolecomposites.realm.RealmRoleCompositeImportService;
import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import io.github.doriangrelu.keycloak.config.service.state.StateService;
import io.github.doriangrelu.keycloak.config.util.CloneUtil;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RoleImportService {
    private static final Logger logger = LoggerFactory.getLogger(RoleImportService.class);
    private static final String[] propertiesWithDependencies = new String[]{
            "composites",
    };

    private final RealmRoleCompositeImportService realmRoleCompositeImport;
    private final ClientRoleCompositeImportService clientRoleCompositeImport;

    private final ClientRepository clientRepository;
    private final RoleRepository roleRepository;
    private final ImportConfigProperties importConfigProperties;
    private final StateService stateService;

    @Autowired
    public RoleImportService(
            final RealmRoleCompositeImportService realmRoleCompositeImportService,
            final ClientRoleCompositeImportService clientRoleCompositeImportService, final ClientRepository clientRepository,
            final RoleRepository roleRepository,
            final ImportConfigProperties importConfigProperties, final StateService stateService) {
        this.realmRoleCompositeImport = realmRoleCompositeImportService;
        this.clientRoleCompositeImport = clientRoleCompositeImportService;
        this.clientRepository = clientRepository;
        this.roleRepository = roleRepository;
        this.importConfigProperties = importConfigProperties;
        this.stateService = stateService;
    }

    public void doImport(final RealmImport realmImport) {
        final RolesRepresentation roles = realmImport.getRoles();
        if (roles == null) {
            return;
        }

        final String realmName = realmImport.getRealm();

        final boolean realmRoleInImport = roles.getRealm() != null;
        final boolean clientRoleInImport = roles.getClient() != null;

        List<RoleRepresentation> existingRealmRoles = null;
        Map<String, List<RoleRepresentation>> existingClientRoles = null;

        if (realmRoleInImport) {
            existingRealmRoles = this.roleRepository.getRealmRoles(realmName);
        }
        if (clientRoleInImport) {
            existingClientRoles = this.roleRepository.getClientRoles(realmName);
        }

        if (realmRoleInImport) {
            this.createOrUpdateRealmRoles(realmName, roles.getRealm(), existingRealmRoles);
        }
        if (clientRoleInImport) {
            this.createOrUpdateClientRoles(realmName, roles.getClient(), existingClientRoles);
        }


        if (realmRoleInImport) {
            this.realmRoleCompositeImport.update(realmName, roles.getRealm());
        }
        if (clientRoleInImport) {
            this.clientRoleCompositeImport.update(realmName, roles.getClient());
        }

    }

    private void createOrUpdateRealmRoles(
            final String realmName,
            final List<RoleRepresentation> rolesToImport,
            final List<RoleRepresentation> existingRealmRoles
    ) {
        final Consumer<RoleRepresentation> loop = role -> this.createOrUpdateRealmRole(realmName, role, existingRealmRoles);
        if (this.importConfigProperties.isParallel()) {
            rolesToImport.parallelStream().forEach(loop);
        } else {
            rolesToImport.forEach(loop);
        }
        ExecutionContextHolder.context().put(realmName, RoleRepresentation.class, rolesToImport);
    }

    private void createOrUpdateRealmRole(
            final String realmName,
            final RoleRepresentation roleToImport,
            final List<RoleRepresentation> existingRoles
    ) {
        final String roleName = roleToImport.getName();

        final RoleRepresentation existingRole = existingRoles.stream()
                .filter(r -> Objects.equals(r.getName(), roleToImport.getName()))
                .findFirst().orElse(null);

        if (existingRole != null) {
            this.updateRoleIfNeeded(realmName, existingRole, roleToImport);
        } else {
            this.createRole(realmName, roleToImport, roleName);
        }
    }

    private void createRole(final String realmName, final RoleRepresentation roleToImport, final String roleName) {
        logger.debug("Create realm-level role '{}' in realm '{}'", roleName, realmName);
        final RoleRepresentation roleToImportWithoutDependencies = CloneUtil.deepClone(
                roleToImport, RoleRepresentation.class, propertiesWithDependencies
        );

        this.roleRepository.createRealmRole(realmName, roleToImportWithoutDependencies);
    }

    private void createOrUpdateClientRoles(
            final String realmName,
            final Map<String, List<RoleRepresentation>> rolesToImport,
            final Map<String, List<RoleRepresentation>> existingRoles
    ) {
        for (final Map.Entry<String, List<RoleRepresentation>> client : rolesToImport.entrySet()) {
            final String clientId = client.getKey();
            final List<RoleRepresentation> clientRoles = client.getValue();

            for (final RoleRepresentation role : clientRoles) {
                this.createOrUpdateClientRole(realmName, clientId, role, existingRoles);
            }
        }

        rolesToImport.forEach((clientId, roles) -> {
            final String key = computeClientRepresentationKey(realmName, clientId);
            ExecutionContextHolder.context().put(key, RoleRepresentation.class, roles);
        });
    }

    private static String computeClientRepresentationKey(final String realmName, final String clientId) {
        return realmName + ':' + clientId;
    }

    private void createOrUpdateClientRole(
            final String realmName,
            final String clientId,
            final RoleRepresentation roleToImport,
            final Map<String, List<RoleRepresentation>> existingRoles
    ) {
        final String roleName = roleToImport.getName();

        if (!existingRoles.containsKey(clientId)) {
            throw new ImportProcessingException(String.format(
                    "Can't create role '%s' for non existing client '%s' in realm '%s'!",
                    roleName, clientId, realmName
            ));
        }

        final RoleRepresentation existingClientRole = existingRoles.get(clientId).stream()
                .filter(r -> Objects.equals(r.getName(), roleToImport.getName()))
                .findFirst().orElse(null);

        if (existingClientRole != null) {
            this.updateClientRoleIfNecessary(realmName, clientId, existingClientRole, roleToImport);
        } else {
            this.createClientRole(realmName, clientId, roleToImport, roleName);
        }
    }

    private void createClientRole(final String realmName, final String clientId, final RoleRepresentation roleToImport, final String roleName) {
        logger.debug("Create client-level role '{}' for client '{}' in realm '{}'", roleName, clientId, realmName);
        final RoleRepresentation roleToImportWithoutDependencies = CloneUtil.deepClone(
                roleToImport, RoleRepresentation.class, propertiesWithDependencies
        );
        this.roleRepository.createClientRole(realmName, clientId, roleToImportWithoutDependencies);
    }

    private void updateRoleIfNeeded(
            final String realmName,
            final RoleRepresentation existingRole,
            final RoleRepresentation roleToImport
    ) {
        final String roleName = roleToImport.getName();
        final RoleRepresentation patchedRole = CloneUtil.patch(existingRole, roleToImport, propertiesWithDependencies);
        if (roleToImport.getAttributes() != null) {
            patchedRole.setAttributes(roleToImport.getAttributes());
        }

        if (!CloneUtil.deepEquals(existingRole, patchedRole)) {
            logger.debug("Update realm-level role '{}' in realm '{}'", roleName, realmName);
            this.roleRepository.updateRealmRole(realmName, patchedRole);
        } else {
            logger.debug("No need to update realm-level '{}' in realm '{}'", roleName, realmName);
        }
    }

    private void updateClientRoleIfNecessary(
            final String realmName,
            final String clientId,
            final RoleRepresentation existingRole,
            final RoleRepresentation roleToImport
    ) {
        final RoleRepresentation patchedRole = CloneUtil.patch(existingRole, roleToImport, propertiesWithDependencies);
        final String roleName = existingRole.getName();

        if (CloneUtil.deepEquals(existingRole, patchedRole)) {
            logger.debug("No need to update client-level role '{}' for client '{}' in realm '{}'", roleName, clientId, realmName);
        } else {
            logger.debug("Update client-level role '{}' for client '{}' in realm '{}'", roleName, clientId, realmName);
            this.roleRepository.updateClientRole(realmName, clientId, patchedRole);
        }
    }

    public void deleteRealmRolesMissingInImport(final RealmImport realmImport) {
        final Set<String> importedRealmRoles = ExecutionContextHolder.context().get(realmImport.getRealm(), RoleRepresentation.class).stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());

        this.roleRepository.getRealmRoles(realmImport.getRealm()).stream()
                .filter(roleRepresentation -> !roleRepresentation.getClientRole())
                .filter(roleRepresentation -> !importedRealmRoles.contains(roleRepresentation.getName()))
                .forEach(roleRepresentation -> {
                    logger.debug("Delete realm-level role '{}' in realm '{}'", roleRepresentation.getName(), realmImport.getRealm());
                    this.roleRepository.deleteRealmRole(realmImport.getRealm(), roleRepresentation);
                });
    }

    public void deleteClientRolesMissingInImport(final RealmImport realmImport) {
        final Map<String, List<RoleRepresentation>> roles = this.roleRepository.getClientRoles(realmImport.getRealm());

        roles.forEach((clientId, roleRepresentations) -> {
            final String key = computeClientRepresentationKey(realmImport.getRealm(), clientId);

            final Collection<String> importedRoles = ExecutionContextHolder.context().get(key, RoleRepresentation.class).stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());

            roleRepresentations.stream()
                    .filter(roleRepresentation -> !importedRoles.contains(roleRepresentation.getName()))
                    .forEach(roleRepresentation -> this.roleRepository.deleteClientRole(realmImport.getRealm(), clientId, roleRepresentation));
        });
    }

}
