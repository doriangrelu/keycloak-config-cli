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

package io.github.doriangrelu.keycloak.config.domain.definition;

/**
 * The flows of a client, as the export format of Keycloak writes them.
 *
 * @param publicClient {@code publicClient}
 * @param standardFlow {@code standardFlowEnabled}
 * @param implicitFlow {@code implicitFlowEnabled}
 * @param directAccessGrants {@code directAccessGrantsEnabled}
 * @param serviceAccounts {@code serviceAccountsEnabled}
 */
public record ClientFlows(
        boolean publicClient,
        boolean standardFlow,
        boolean implicitFlow,
        boolean directAccessGrants,
        boolean serviceAccounts) {

    /** The value of each flow when a file does not write it. */
    public static final ClientFlows KEYCLOAK_DEFAULTS = new ClientFlows(false, true, false, true, false);

    /** The flows of an application type (ADR-008). */
    public static ClientFlows of(final ApplicationType type) {
        return switch (type) {
            case SPA -> new ClientFlows(true, true, false, false, false);
            case WEB_APPLICATION -> new ClientFlows(false, true, false, false, false);
            case SERVICE -> new ClientFlows(false, false, false, false, true);
            case API -> new ClientFlows(false, false, false, false, false);
            case CUSTOM -> throw new IllegalArgumentException("A custom client has no flows of its own");
        };
    }
}
