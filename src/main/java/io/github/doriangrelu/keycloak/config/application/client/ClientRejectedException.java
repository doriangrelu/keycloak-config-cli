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

package io.github.doriangrelu.keycloak.config.application.client;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** Keycloak refused to create or update a client, or the import asks to create one of Keycloak's own clients. */
public class ClientRejectedException extends ApplicationException {

    private ClientRejectedException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public static ClientRejectedException onCreate(final String client, final String realm, final Throwable cause) {
        return new ClientRejectedException(
                String.format("Cannot create client '%s' in realm '%s': %s", client, realm, cause.getMessage()), cause);
    }

    public static ClientRejectedException onUpdate(final String client, final String realm, final Throwable cause) {
        return new ClientRejectedException(
                String.format("Cannot update client '%s' in realm '%s': %s", client, realm, cause.getMessage()), cause);
    }

    public static ClientRejectedException systemClient(final String client, final String realm) {
        return new ClientRejectedException(
                String.format(
                        "Cannot create system client '%s' in realm '%s': System clients should be auto-created by"
                                + " Keycloak",
                        client, realm),
                null);
    }
}
