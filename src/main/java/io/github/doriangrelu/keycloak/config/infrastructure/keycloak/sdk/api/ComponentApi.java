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
import org.keycloak.representations.idm.ComponentRepresentation;

/** Component operations of the Keycloak Admin API (ADR-003). */
public interface ComponentApi {

    /** Id of the realm: the parent id of its top-level components. */
    String realmId(String realm);

    /** Every component of the realm, sub-components included. */
    List<ComponentRepresentation> listComponents(String realm);

    /** @return the id of the created component */
    String createComponent(String realm, ComponentRepresentation component);

    void updateComponent(String realm, ComponentRepresentation component);

    void deleteComponent(String realm, String id);

    void syncUsers(String realm, String userStorageId);
}
