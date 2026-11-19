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

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.legacy.LegacyReconciliationBridge;
import io.github.doriangrelu.keycloak.config.model.ProtectedResource;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import io.github.doriangrelu.keycloak.config.repository.RealmRepository;
import io.github.doriangrelu.keycloak.config.service.checksum.ChecksumService;
import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import io.github.doriangrelu.keycloak.config.service.state.StateService;
import java.util.Set;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RealmImportService {
    private static final Logger logger = LoggerFactory.getLogger(RealmImportService.class);
    private final KeycloakProvider keycloakProvider;
    private final RealmRepository realmRepository;

    private final LegacyReconciliationBridge reconciliationBridge;
    private final ComponentGateway componentGateway;

    private final ImportConfigProperties importProperties;

    private final ChecksumService checksumService;
    private final StateService stateService;

    @Autowired
    public RealmImportService(
            ImportConfigProperties importProperties,
            KeycloakProvider keycloakProvider,
            RealmRepository realmRepository,
            LegacyReconciliationBridge reconciliationBridge,
            ComponentGateway componentGateway,
            ChecksumService checksumService,
            StateService stateService) {
        this.importProperties = importProperties;
        this.keycloakProvider = keycloakProvider;
        this.realmRepository = realmRepository;
        this.reconciliationBridge = reconciliationBridge;
        this.componentGateway = componentGateway;
        this.checksumService = checksumService;
        this.stateService = stateService;
    }

    public void doImport(RealmImport realmImport) {
        ExecutionContextHolder.context()
                .put(realmImport.getRealm(), ProtectedResource.class, realmImport.getProtectedResources());

        boolean realmExists = realmRepository.exists(realmImport.getRealm());
        trackImport(realmImport, realmExists);

        if (realmExists) {
            updateRealmIfNecessary(realmImport);
        } else {
            createRealm(realmImport);
        }
    }

    /**
     * Prepares the clean phase: captures the remote state before this import overwrites it, and records what the
     * import declares. Done for every file, including those skipped because their checksum did not change.
     */
    private void trackImport(RealmImport realmImport, boolean realmExists) {
        String realmName = realmImport.getRealm();
        boolean remoteStateLoaded =
                realmExists && importProperties.getRemoteState().isEnabled();
        if (remoteStateLoaded) {
            stateService.loadState(realmImport);
        }
        reconciliationBridge.startFile(realmImport, realmExists, remoteStateLoaded);
    }

    private void updateRealmIfNecessary(RealmImport realmImport) {
        if (!importProperties.getCache().isEnabled() || checksumService.hasToBeUpdated(realmImport)) {
            updateRealm(realmImport);
        } else {
            logger.debug(
                    "No need to update realm '{}', import checksum same: '{}'",
                    realmImport.getRealm(),
                    realmImport.getChecksum());
        }
    }

    private void createRealm(RealmImport realmImport) {
        logger.debug("Creating realm '{}' ...", realmImport.getRealm());

        reconciliationBridge.apply(realmImport, Set.of(ResourceType.REALM_SETTINGS));

        // refresh the access token to update the scopes. See: https://github.com/adorsys/keycloak-config-cli/issues/339
        keycloakProvider.refreshToken();

        stateService.loadState(realmImport);
        configureRealm(realmImport);
    }

    private void updateRealm(RealmImport realmImport) {
        logger.debug("Updating realm '{}'...", realmImport.getRealm());

        // Settings omitted by the import keep their value, events and keycloak-config-cli attributes included
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.REALM_SETTINGS));
        // The state is written back at the end of the import with the attributes read here: the updated ones
        stateService.loadState(realmImport);

        configureRealm(realmImport);
    }

    private void configureRealm(RealmImport realmImport) {
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.CLIENT_SCOPE));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.DEFAULT_CLIENT_SCOPE));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.CLIENT_PROFILE, ResourceType.CLIENT_POLICY));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.CLIENT));
        reconciliationBridge.apply(
                realmImport,
                Set.of(
                        ResourceType.REALM_ROLE,
                        ResourceType.CLIENT_ROLE,
                        ResourceType.ROLE_COMPOSITE,
                        ResourceType.REQUIRED_ACTION,
                        ResourceType.MESSAGE_BUNDLE,
                        ResourceType.GROUP));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.DEFAULT_GROUP));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.COMPONENT, ResourceType.SUB_COMPONENT));
        syncUserFederationIfNecessary(realmImport);
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.USER_PROFILE));
        if (realmImport.getUsers() != null && realmImport.getUsers().isEmpty()) {
            logger.warn("Purging users isn't supported in keycloak-config-cli!");
        }
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.USER));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.AUTHENTICATION_FLOW));
        bindRealmFlows(realmImport);
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.AUTHENTICATOR_CONFIG));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.CLIENT_FLOW_BINDING));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.DEFAULT_CLIENT_SCOPE));
        reconciliationBridge.apply(
                realmImport, Set.of(ResourceType.IDENTITY_PROVIDER, ResourceType.IDENTITY_PROVIDER_MAPPER));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.CLIENT_AUTHORIZATION));
        reconciliationBridge.apply(realmImport, Set.of(ResourceType.SCOPE_MAPPING, ResourceType.CLIENT_SCOPE_MAPPING));

        stateService.doImport(realmImport);
        checksumService.doImport(realmImport);
    }

    /** Binds the flows of the realm once its declared flows exist. */
    private void bindRealmFlows(RealmImport realmImport) {
        if (realmImport.getAuthenticationFlows() == null) {
            return;
        }
        RealmRepresentation realm = realmRepository.get(realmImport.getRealm());
        realm.setBrowserFlow(realmImport.getBrowserFlow());
        realm.setDirectGrantFlow(realmImport.getDirectGrantFlow());
        realm.setClientAuthenticationFlow(realmImport.getClientAuthenticationFlow());
        realm.setDockerAuthenticationFlow(realmImport.getDockerAuthenticationFlow());
        realm.setRegistrationFlow(realmImport.getRegistrationFlow());
        realm.setResetCredentialsFlow(realmImport.getResetCredentialsFlow());
        realmRepository.update(realm);
    }

    private void syncUserFederationIfNecessary(RealmImport realmImport) {
        if (importProperties.getBehaviors().isSyncUserFederation()
                && realmImport.getComponents() != null
                && realmImport.getComponents().containsKey("org.keycloak.storage.UserStorageProvider")) {
            componentGateway.syncImportingUserStorages(realmImport.getRealm());
        }
    }

    /**
     * Performs cleanup operations on the realm after the import configuration has been applied.
     *
     * <p>The reconciliation engine deletes the managed resources of the migrated types missing from every import
     * file of the realm.</p>
     *
     * @param realmImport the realm import configuration identifying the target realm
     */
    public void cleanRealm(final RealmImport realmImport) {
        logger.debug("Start cleaning realm import for realm part '{}'", realmImport.getRealm());
        // Roles: managed mode, remote state and protections are applied by the reconciliation engine
        this.reconciliationBridge.prune(realmImport);
    }
}
