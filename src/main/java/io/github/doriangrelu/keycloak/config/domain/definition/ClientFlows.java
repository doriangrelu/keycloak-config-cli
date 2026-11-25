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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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

    /** The keys of the five flows in a client, in the order of this record. */
    public static final List<String> KEYS = List.of(
            "publicClient",
            "standardFlowEnabled",
            "implicitFlowEnabled",
            "directAccessGrantsEnabled",
            "serviceAccountsEnabled");

    /**
     * The flows written in a client; a flow not written takes its default. Empty when a flow is not a boolean (a
     * placeholder, for instance), as the flows are then only known at import. A boolean is read in any case.
     */
    public static Optional<ClientFlows> read(final Function<String, Optional<String>> flowText) {
        boolean[] values = new boolean[KEYS.size()];
        boolean[] defaults = {
            KEYCLOAK_DEFAULTS.publicClient(),
            KEYCLOAK_DEFAULTS.standardFlow(),
            KEYCLOAK_DEFAULTS.implicitFlow(),
            KEYCLOAK_DEFAULTS.directAccessGrants(),
            KEYCLOAK_DEFAULTS.serviceAccounts()
        };
        for (int i = 0; i < values.length; i++) {
            Optional<String> text = flowText.apply(KEYS.get(i));
            if (text.isEmpty()) {
                values[i] = defaults[i];
            } else if ("true".equalsIgnoreCase(text.get())) {
                values[i] = true;
            } else if ("false".equalsIgnoreCase(text.get())) {
                values[i] = false;
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(new ClientFlows(values[0], values[1], values[2], values[3], values[4]));
    }

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
