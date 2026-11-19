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

package io.github.doriangrelu.keycloak.config.domain.reconcile;

/** Resource types handled by the reconciliation engine. Grows as resource types are migrated (EPIC-003). */
public enum ResourceType {
    REALM_ROLE("Realm role"),
    CLIENT_ROLE("Client role"),
    ROLE_COMPOSITE("Role composites"),
    REQUIRED_ACTION("Required action"),
    MESSAGE_BUNDLE("Message bundle"),
    CLIENT_SCOPE("Client scope"),
    CLIENT("Client"),
    GROUP("Group"),
    USER("User"),
    IDENTITY_PROVIDER("Identity provider"),
    IDENTITY_PROVIDER_MAPPER("Identity provider mapper"),
    CLIENT_PROFILE("Client profile"),
    CLIENT_POLICY("Client policy"),
    SCOPE_MAPPING("Scope mapping"),
    CLIENT_SCOPE_MAPPING("Client scope mapping"),
    COMPONENT("Component"),
    SUB_COMPONENT("Sub-component"),
    USER_PROFILE("User profile"),
    AUTHENTICATION_FLOW("Authentication flow"),
    AUTHENTICATOR_CONFIG("Authenticator config"),
    CLIENT_FLOW_BINDING("Client flow binding"),
    CLIENT_AUTHORIZATION("Client authorization");

    private final String label;

    ResourceType(final String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
