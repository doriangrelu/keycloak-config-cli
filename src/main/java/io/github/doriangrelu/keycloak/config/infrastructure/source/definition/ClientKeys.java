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
        ApplicationType type = ApplicationType.deduce(client::text);
        // A client may be given by name only: its name is then how it is known
        String clientId = client.text("clientId").or(() -> client.text("name")).orElse("");
        Map<String, TemplatedValue> attributes = new LinkedHashMap<>();
        client.textEntries("attributes").forEach((key, value) -> attributes.put(key, TemplatedValue.of(value)));
        return new ClientDefinition(
                TemplatedValue.of(clientId),
                optional(client, "name"),
                optional(client, "description"),
                type,
                client.texts("redirectUris").stream().map(TemplatedValue::of).toList(),
                client.texts("webOrigins").stream().map(TemplatedValue::of).toList(),
                attributes,
                optional(client, "secret"));
    }

    /**
     * Writes the keys the editing model manages; the other keys of the client stay. The flows of a custom client are
     * left as they are, and {@code bearerOnly} is never written.
     */
    static void write(final ClientDefinition client, final TreeObject object) {
        object.setText("clientId", client.clientId());
        setOrRemove(object, "name", client.name());
        setOrRemove(object, "description", client.description());
        setOrRemove(object, "secret", client.secret());
        if (client.applicationType() != ApplicationType.CUSTOM) {
            ClientFlows flows = ClientFlows.of(client.applicationType());
            ClientFlows defaults = ClientFlows.KEYCLOAK_DEFAULTS;
            setFlow(object, "publicClient", flows.publicClient(), defaults.publicClient());
            setFlow(object, "standardFlowEnabled", flows.standardFlow(), defaults.standardFlow());
            setFlow(object, "implicitFlowEnabled", flows.implicitFlow(), defaults.implicitFlow());
            setFlow(object, "directAccessGrantsEnabled", flows.directAccessGrants(), defaults.directAccessGrants());
            setFlow(object, "serviceAccountsEnabled", flows.serviceAccounts(), defaults.serviceAccounts());
        }
        setOrRemove(object, "redirectUris", client.redirectUris());
        setOrRemove(object, "webOrigins", client.webOrigins());
        if (!client.attributes().isEmpty()) {
            TreeObject attributes = object.objectOrCreate("attributes");
            client.attributes().forEach(attributes::setText);
        }
    }

    /**
     * Writes a flow only when its effective value, as written or else the default of Keycloak, is not the one the type
     * needs: a file stays minimal, and a diff only shows what changes.
     */
    private static void setFlow(
            final TreeObject object, final String key, final boolean value, final boolean keycloakDefault) {
        if (object.bool(key).orElse(keycloakDefault) != value) {
            object.setBool(key, value);
        }
    }

    static void setOrRemove(final TreeObject object, final String key, final Optional<TemplatedValue> value) {
        value.ifPresentOrElse(text -> object.setText(key, text), () -> object.remove(key));
    }

    static void setOrRemove(final TreeObject object, final String key, final java.util.List<TemplatedValue> values) {
        if (values.isEmpty()) {
            object.remove(key);
        } else {
            object.setTexts(key, values);
        }
    }

    private static Optional<TemplatedValue> optional(final TreeObject object, final String key) {
        return object.text(key).map(TemplatedValue::of);
    }
}
