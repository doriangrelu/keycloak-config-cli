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
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImport;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImportReconciliation;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final RealmGateway realmGateway;

    private final RealmImportReconciliation reconciliation;
    private final ComponentGateway componentGateway;

    private final ImportConfigProperties importProperties;

    @Autowired
    public RealmImportService(
            ImportConfigProperties importProperties,
            KeycloakProvider keycloakProvider,
            RealmGateway realmGateway,
            RealmImportReconciliation reconciliation,
            ComponentGateway componentGateway) {
        this.importProperties = importProperties;
        this.keycloakProvider = keycloakProvider;
        this.realmGateway = realmGateway;
        this.reconciliation = reconciliation;
        this.componentGateway = componentGateway;
    }

    public void doImport(RealmImport realmImport) {
        boolean realmExists = realmGateway.findSettings(realmImport.getRealm()).isPresent();
        trackImport(realmImport, realmExists);

        if (realmExists) {
            updateRealm(realmImport);
        } else {
            createRealm(realmImport);
        }
    }

    /** Prepares the clean phase: records what the import declares. */
    private void trackImport(RealmImport realmImport, boolean realmExists) {
        reconciliation.startFile(realmImport, realmExists);
    }

    private void createRealm(RealmImport realmImport) {
        logger.debug("Creating realm '{}' ...", realmImport.getRealm());

        reconciliation.apply(realmImport, Set.of(ResourceType.REALM_SETTINGS));

        if (!importProperties.isDryRun()) {
            // refresh the access token to update the scopes. See:
            // https://github.com/adorsys/keycloak-config-cli/issues/339
            keycloakProvider.refreshToken();
        }
        configureRealm(realmImport);
    }

    private void updateRealm(RealmImport realmImport) {
        logger.debug("Updating realm '{}'...", realmImport.getRealm());

        // Settings omitted by the import keep their value, events included
        reconciliation.apply(realmImport, Set.of(ResourceType.REALM_SETTINGS));

        configureRealm(realmImport);
    }

    private void configureRealm(RealmImport realmImport) {
        reconciliation.apply(realmImport, Set.of(ResourceType.CLIENT_SCOPE));
        reconciliation.apply(realmImport, Set.of(ResourceType.DEFAULT_CLIENT_SCOPE));
        reconciliation.apply(realmImport, Set.of(ResourceType.CLIENT_PROFILE, ResourceType.CLIENT_POLICY));
        reconciliation.apply(realmImport, Set.of(ResourceType.CLIENT));
        reconciliation.apply(
                realmImport,
                Set.of(
                        ResourceType.REALM_ROLE,
                        ResourceType.CLIENT_ROLE,
                        ResourceType.ROLE_COMPOSITE,
                        ResourceType.REQUIRED_ACTION,
                        ResourceType.MESSAGE_BUNDLE,
                        ResourceType.GROUP));
        reconciliation.apply(realmImport, Set.of(ResourceType.DEFAULT_GROUP));
        reconciliation.apply(realmImport, Set.of(ResourceType.COMPONENT, ResourceType.SUB_COMPONENT));
        if (!importProperties.isDryRun()) {
            syncUserFederationIfNecessary(realmImport);
        }
        reconciliation.apply(realmImport, Set.of(ResourceType.USER_PROFILE));
        if (realmImport.getUsers() != null && realmImport.getUsers().isEmpty()) {
            logger.warn("Purging users isn't supported in keycloak-config-cli!");
        }
        reconciliation.apply(realmImport, Set.of(ResourceType.USER));
        reconciliation.apply(realmImport, Set.of(ResourceType.AUTHENTICATION_FLOW));
        reconciliation.apply(realmImport, Set.of(ResourceType.REALM_FLOW_BINDING));
        reconciliation.apply(realmImport, Set.of(ResourceType.AUTHENTICATOR_CONFIG));
        reconciliation.apply(realmImport, Set.of(ResourceType.CLIENT_FLOW_BINDING));
        reconciliation.apply(realmImport, Set.of(ResourceType.DEFAULT_CLIENT_SCOPE));
        reconciliation.apply(
                realmImport, Set.of(ResourceType.IDENTITY_PROVIDER, ResourceType.IDENTITY_PROVIDER_MAPPER));
        reconciliation.apply(realmImport, Set.of(ResourceType.CLIENT_AUTHORIZATION));
        reconciliation.apply(realmImport, Set.of(ResourceType.SCOPE_MAPPING, ResourceType.CLIENT_SCOPE_MAPPING));
    }

    private void syncUserFederationIfNecessary(RealmImport realmImport) {
        if (importProperties.getBehaviors().isSyncUserFederation()
                && realmImport.getComponents() != null
                && realmImport.getComponents().containsKey("org.keycloak.storage.UserStorageProvider")) {
            componentGateway.syncImportingUserStorages(realmImport.getRealm());
        }
    }

    /**
     * Imports the files of one run of the command line: each file in turn, then the clean phase of each realm,
     * which deletes the managed resources missing from every file of the realm.
     */
    public void importAll(final Collection<RealmImport> realmImports) {
        reconciliation.startRun();
        realmImports.forEach(this::doImport);
        realmImports.forEach(reconciliation::prune);
    }

    /** Changes of the last run, by realm: applied, or only planned in a dry run. */
    public Map<String, List<PlannedChange>> plans() {
        return reconciliation.plans();
    }
}
