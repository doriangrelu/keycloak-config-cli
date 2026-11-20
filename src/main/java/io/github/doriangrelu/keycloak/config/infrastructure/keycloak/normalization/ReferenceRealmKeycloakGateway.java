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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import io.github.doriangrelu.keycloak.config.application.normalization.port.ReferenceRealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reference realms shipped with keycloak-config-cli ({@code baseline/<version>/}), read as exports. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
class ReferenceRealmKeycloakGateway implements ReferenceRealmGateway {

    private final BaselineProvider baselineProvider;
    private final RealmExportReader reader;
    private final ClientKeycloakMapper clientMapper;

    ReferenceRealmKeycloakGateway(
            final BaselineProvider baselineProvider,
            final RealmExportReader reader,
            final ClientKeycloakMapper clientMapper) {
        this.baselineProvider = baselineProvider;
        this.reader = reader;
        this.clientMapper = clientMapper;
    }

    @Override
    public DesiredState referenceRealm(final String keycloakVersion, final String realm) {
        return this.reader.read(this.baselineProvider.getRealm(keycloakVersion, realm));
    }

    @Override
    public Client referenceClient(final String keycloakVersion, final String clientId) {
        return this.clientMapper.toClient(this.baselineProvider.getClient(keycloakVersion, clientId));
    }
}
