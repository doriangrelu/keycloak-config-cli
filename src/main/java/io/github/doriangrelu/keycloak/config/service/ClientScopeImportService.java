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
import io.github.doriangrelu.keycloak.config.repository.ClientScopeRepository;
import io.github.doriangrelu.keycloak.config.repository.RealmRepository;
import java.util.List;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ClientScopeImportService {
    private static final Logger logger = LoggerFactory.getLogger(ClientScopeImportService.class);

    private final ClientScopeRepository clientScopeRepository;
    private final ImportConfigProperties importConfigProperties;
    private final RealmRepository realmRepository;

    public ClientScopeImportService(
            ClientScopeRepository clientScopeRepository,
            ImportConfigProperties importConfigProperties,
            RealmRepository realmRepository) {
        this.clientScopeRepository = clientScopeRepository;
        this.importConfigProperties = importConfigProperties;
        this.realmRepository = realmRepository;
    }

    public void updateDefaultClientScopes(RealmImport realmImport, RealmRepresentation existingRealm) {
        final List<ClientScopeRepresentation> defaultDefaultClientScopes =
                realmRepository.getResource(realmImport.getRealm()).getDefaultDefaultClientScopes();
        final List<String> existingDefaultDefaultClientScopes = defaultDefaultClientScopes.stream()
                .map(ClientScopeRepresentation::getName)
                .toList();

        final List<ClientScopeRepresentation> defaultOptionalClientScopes =
                realmRepository.getResource(realmImport.getRealm()).getDefaultOptionalClientScopes();

        final List<String> existingDefaultOptionalClientScopes = defaultOptionalClientScopes.stream()
                .map(ClientScopeRepresentation::getName)
                .toList();

        removeDefaultDefaultClientScopes(realmImport, existingDefaultDefaultClientScopes);
        removeDefaultOptionalClientScopes(realmImport, existingDefaultOptionalClientScopes);

        addDefaultDefaultClientScopes(realmImport, existingDefaultDefaultClientScopes);
        addDefaultOptionalClientScopes(realmImport, existingDefaultOptionalClientScopes);

        existingRealm.setDefaultDefaultClientScopes(realmImport.getDefaultDefaultClientScopes());
        existingRealm.setDefaultOptionalClientScopes(realmImport.getDefaultOptionalClientScopes());
    }

    private void addDefaultDefaultClientScopes(RealmImport realmImport, List<String> existingDefaultClientScopes) {
        if (realmImport.getDefaultDefaultClientScopes() == null) return;

        for (String scope : realmImport.getDefaultDefaultClientScopes()) {
            if (existingDefaultClientScopes != null && existingDefaultClientScopes.contains(scope)) continue;

            ClientScopeRepresentation scopeResource = clientScopeRepository.getByName(realmImport.getRealm(), scope);

            if (scopeResource == null) {
                throw new ImportProcessingException(String.format(
                        "Could not find client scope '%s' in realm '%s'!", scope, realmImport.getRealm()));
            }

            realmRepository.addDefaultDefaultClientScope(realmImport.getRealm(), scopeResource.getId());
        }
    }

    private void addDefaultOptionalClientScopes(RealmImport realmImport, List<String> existingDefaultClientScopes) {
        if (realmImport.getDefaultOptionalClientScopes() == null) return;

        for (String scope : realmImport.getDefaultOptionalClientScopes()) {
            if (existingDefaultClientScopes != null && existingDefaultClientScopes.contains(scope)) continue;

            ClientScopeRepresentation scopeResource = clientScopeRepository.getByName(realmImport.getRealm(), scope);

            if (scopeResource == null) {
                throw new ImportProcessingException(String.format(
                        "Could not find client scope '%s' in realm '%s'!", scope, realmImport.getRealm()));
            }

            realmRepository.addDefaultOptionalClientScope(realmImport.getRealm(), scopeResource.getId());
        }
    }

    private void removeDefaultDefaultClientScopes(RealmImport realmImport, List<String> existingDefaultClientScopes) {
        if (realmImport.getDefaultDefaultClientScopes() == null || existingDefaultClientScopes == null) return;

        for (String scope : existingDefaultClientScopes) {
            if (realmImport.getDefaultDefaultClientScopes().contains(scope)) continue;

            ClientScopeRepresentation scopeResource = clientScopeRepository.getByName(realmImport.getRealm(), scope);
            realmRepository.removeDefaultDefaultClientScope(realmImport.getRealm(), scopeResource.getId());
        }
    }

    private void removeDefaultOptionalClientScopes(RealmImport realmImport, List<String> existingDefaultClientScopes) {
        if (realmImport.getDefaultOptionalClientScopes() == null || existingDefaultClientScopes == null) return;

        for (String scope : existingDefaultClientScopes) {
            if (realmImport.getDefaultOptionalClientScopes().contains(scope)) continue;

            ClientScopeRepresentation scopeResource = clientScopeRepository.getByName(realmImport.getRealm(), scope);
            realmRepository.removeDefaultOptionalClientScope(realmImport.getRealm(), scopeResource.getId());
        }
    }
}
