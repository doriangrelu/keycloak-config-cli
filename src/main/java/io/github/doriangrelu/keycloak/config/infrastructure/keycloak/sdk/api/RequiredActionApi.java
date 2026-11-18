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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api;

import java.util.List;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;

/** Required action operations of the Keycloak Admin API (ADR-003). */
public interface RequiredActionApi {

    List<RequiredActionProviderRepresentation> listRequiredActions(String realm);

    /** Registers a required action: Keycloak only takes its provider and name, and uses the provider as alias. */
    void registerRequiredAction(String realm, String providerId, String name);

    void updateRequiredAction(String realm, String alias, RequiredActionProviderRepresentation requiredAction);

    void removeRequiredAction(String realm, String alias);
}
