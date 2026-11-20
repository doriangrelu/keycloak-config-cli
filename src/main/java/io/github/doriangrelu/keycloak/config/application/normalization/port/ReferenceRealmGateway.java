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

package io.github.doriangrelu.keycloak.config.application.normalization.port;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;

/** Reference realms: realms as Keycloak creates them, for each supported version. */
public interface ReferenceRealmGateway {

    /**
     * The resources of the reference realm of a Keycloak version, named as the exported realm: some of its values,
     * such as the default role or the redirect URIs of its clients, hold the name of the realm.
     */
    DesiredState referenceRealm(String keycloakVersion, String realm);

    /** A client as Keycloak creates it with default values, with the given client id. */
    Client referenceClient(String keycloakVersion, String clientId);
}
