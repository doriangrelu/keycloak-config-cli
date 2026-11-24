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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientFlows;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The keys of a client in the export format of Keycloak, and the editing model (ADR-008). */
final class ClientKeys {

    private ClientKeys() {}

    static ClientDefinition read(final TreeObject client) {
        ClientFlows defaults = ClientFlows.KEYCLOAK_DEFAULTS;
        ClientFlows flows = new ClientFlows(
                client.bool("publicClient").orElse(defaults.publicClient()),
                client.bool("standardFlowEnabled").orElse(defaults.standardFlow()),
                client.bool("implicitFlowEnabled").orElse(defaults.implicitFlow()),
                client.bool("directAccessGrantsEnabled").orElse(defaults.directAccessGrants()),
                client.bool("serviceAccountsEnabled").orElse(defaults.serviceAccounts()));
        // A client may be given by name only: its name is then how it is known
        String clientId = client.text("clientId").or(() -> client.text("name")).orElse("");
        Map<String, TemplatedValue> attributes = new LinkedHashMap<>();
        client.textEntries("attributes").forEach((key, value) -> attributes.put(key, TemplatedValue.of(value)));
        return new ClientDefinition(
                TemplatedValue.of(clientId),
                optional(client, "name"),
                optional(client, "description"),
                ApplicationType.of(flows),
                client.texts("redirectUris").stream().map(TemplatedValue::of).toList(),
                client.texts("webOrigins").stream().map(TemplatedValue::of).toList(),
                attributes);
    }

    private static Optional<TemplatedValue> optional(final TreeObject object, final String key) {
        return object.text(key).map(TemplatedValue::of);
    }
}
