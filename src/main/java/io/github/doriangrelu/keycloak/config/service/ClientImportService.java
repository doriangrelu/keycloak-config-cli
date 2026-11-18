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
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import io.github.doriangrelu.keycloak.config.repository.AuthenticationFlowRepository;
import io.github.doriangrelu.keycloak.config.repository.ClientRepository;
import io.github.doriangrelu.keycloak.config.repository.ClientScopeRepository;
import io.github.doriangrelu.keycloak.config.util.KeycloakUtil;
import io.github.doriangrelu.keycloak.config.util.ResponseUtil;
import jakarta.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.keycloak.representations.idm.ClientRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings({"java:S1192"})
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ClientImportService {
    private static final Logger logger = LoggerFactory.getLogger(ClientImportService.class);

    private static final String[] propertiesWithDependencies = new String[] {
        "authenticationFlowBindingOverrides", "authorizationSettings",
    };

    public static final String REALM_MANAGEMENT_CLIENT_ID = "realm-management";
    public static final String ADMIN_PERMISSIONS_CLIENT_ID = "admin-permissions";

    private final ClientRepository clientRepository;
    private final ClientScopeRepository clientScopeRepository;
    private final AuthenticationFlowRepository authenticationFlowRepository;
    private final ImportConfigProperties importConfigProperties;
    private final KeycloakProvider keycloakProvider;

    @Autowired
    public ClientImportService(
            final ClientRepository clientRepository,
            final ClientScopeRepository clientScopeRepository,
            final AuthenticationFlowRepository authenticationFlowRepository,
            final ImportConfigProperties importConfigProperties,
            final KeycloakProvider keycloakProvider) {
        this.clientRepository = clientRepository;
        this.clientScopeRepository = clientScopeRepository;
        this.authenticationFlowRepository = authenticationFlowRepository;
        this.importConfigProperties = importConfigProperties;
        this.keycloakProvider = keycloakProvider;
    }

    /**
     * Imports the clients defined in the given realm import configuration.
     *
     * <p>If the realm import contains client definitions, each client is created or updated
     * accordingly. The list of processed clients is stored in the {@link io.github.doriangrelu.keycloak.config.service.state.ExecutionContext}
     * for later use by the cleanup phase.</p>
     *
     * @param realmImport the realm import configuration containing clients to import
     */
    /**
     * Imports client dependencies such as authentication flow binding overrides.
     *
     * <p>This method should be called after the initial client import and after authentication
     * flows have been imported, as it resolves flow alias references to their actual IDs.</p>
     *
     * @param realmImport the realm import configuration containing clients with dependencies
     */
    public void doImportDependencies(final RealmImport realmImport) {
        final List<ClientRepresentation> clients = realmImport.getClients();
        if (clients == null) {
            return;
        }
        this.updateClientAuthenticationFlowBindingOverrides(realmImport, clients);
    }

    /**
     * Creates or updates all clients from the import and stores them in the
     * {@link io.github.doriangrelu.keycloak.config.service.state.ExecutionContext} for the given realm.
     *
     * <p>When parallel import is enabled, clients are processed using a parallel stream.</p>
     *
     * @param realmImport the realm import configuration
     * @param clients     the list of client representations to create or update
     */
    /**
     * Deletes clients from the realm that are not present in the import configuration.
     *
     * <p>The set of imported clients is retrieved from the {@link io.github.doriangrelu.keycloak.config.service.state.ExecutionContext},
     * which must have been populated during the {@link #doImport(RealmImport)} phase.
     * Default Keycloak clients, master realm clients ending with {@code -realm}, and
     * system-managed clients (such as {@code admin-permissions} when FGAP V2 is active)
     * are excluded from deletion.</p>
     *
     * <p>When remote state tracking is enabled, only clients previously managed by
     * this tool (present in the state) are considered for removal.</p>
     *
     * @param realmImport the realm import configuration identifying the target realm
     */
    private void updateClient(final String realmName, final ClientRepresentation patchedClient) {
        try {
            this.clientRepository.update(realmName, patchedClient);
        } catch (final WebApplicationException error) {
            int status = -1;
            try {
                status = error.getResponse() != null ? error.getResponse().getStatus() : -1;
            } catch (final Exception e) {
                logger.debug("Unable to get response status from WebApplicationException", e);
            }

            // FGAP V2: admin-permissions client may return 400 when server-managed. Swallow as defensive fallback.
            if (status == 400 && ADMIN_PERMISSIONS_CLIENT_ID.equals(patchedClient.getClientId())) {
                logger.debug(
                        "Skipping update for 'admin-permissions' client in realm '{}' - FGAP V2 manages this client internally",
                        realmName);
                return;
            }

            final String errorMessage = ResponseUtil.getErrorMessage(error);
            throw new ImportProcessingException(
                    String.format(
                            "Cannot update client '%s' in realm '%s': %s",
                            this.getClientIdentifier(patchedClient), realmName, errorMessage),
                    error);
        }
    }

    private void updateClientAuthenticationFlowBindingOverrides(
            final RealmImport realmImport, final List<ClientRepresentation> clients) {
        final String realmName = realmImport.getRealm();

        for (final ClientRepresentation client : clients) {
            final ClientRepresentation existingClient = this.getExistingClient(realmName, client);

            this.updateAuthenticationFlowBindingOverrides(
                    realmName, existingClient, client.getAuthenticationFlowBindingOverrides());
        }
    }

    private void updateAuthenticationFlowBindingOverrides(
            final String realmName,
            final ClientRepresentation existingClient,
            final Map<String, String> authenticationFlowBindingOverrides) {
        final boolean isEqual = Objects.equals(
                authenticationFlowBindingOverrides, existingClient.getAuthenticationFlowBindingOverrides());

        if (isEqual) {
            return;
        }

        final Map<String, String> authFlowUpdates =
                new HashMap<>(existingClient.getAuthenticationFlowBindingOverrides());

        // Be sure that all existing values will be cleared
        // See:
        // https://github.com/keycloak/keycloak/blob/790b549cf99dbbba109e145654ee4a4cd1a047c9/server-spi-private/src/main/java/org/keycloak/models/utils/RepresentationToModel.java#L1516
        authFlowUpdates.replaceAll((k, v) -> v = null);

        // Compute new values
        if (authenticationFlowBindingOverrides != null) {
            for (final Map.Entry<String, String> override : authenticationFlowBindingOverrides.entrySet()) {
                if (override.getValue() == null
                        || override.getValue().isEmpty()
                        || this.authenticationFlowRepository.exists(realmName, override.getValue())) {
                    authFlowUpdates.put(override.getKey(), override.getValue());
                } else {
                    final String flowId = this.authenticationFlowRepository
                            .getByAlias(realmName, override.getValue())
                            .getId();
                    authFlowUpdates.put(override.getKey(), flowId);
                }
            }
        }

        existingClient.setAuthenticationFlowBindingOverrides(authFlowUpdates);
        this.updateClient(realmName, existingClient);
    }

    private String getClientIdentifier(final ClientRepresentation client) {
        return client.getName() != null && !KeycloakUtil.isDefaultClient(client)
                ? client.getName()
                : client.getClientId();
    }

    private ClientRepresentation getExistingClient(final String realmName, final ClientRepresentation client) {
        if (client.getClientId() != null) {
            return this.clientRepository.getByClientId(realmName, client.getClientId());
        } else if (client.getName() != null) {
            return this.clientRepository.getByName(realmName, client.getName());
        } else {
            throw new ImportProcessingException("clients require client id or name.");
        }
    }
}
