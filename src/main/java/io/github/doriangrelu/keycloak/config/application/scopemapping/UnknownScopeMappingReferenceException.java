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

package io.github.doriangrelu.keycloak.config.application.scopemapping;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** A scope mapping refers to a client, a client scope or a role that does not exist in the realm. */
public class UnknownScopeMappingReferenceException extends ApplicationException {

    private UnknownScopeMappingReferenceException(final String message) {
        super(message);
    }

    public static UnknownScopeMappingReferenceException client(final String clientId) {
        return new UnknownScopeMappingReferenceException(
                String.format("Cannot find client by clientId '%s'", clientId));
    }

    public static UnknownScopeMappingReferenceException clientScope(final String name) {
        return new UnknownScopeMappingReferenceException(String.format("Cannot find client-scope by name '%s'", name));
    }

    public static UnknownScopeMappingReferenceException realmRole(final String realm, final String role) {
        return new UnknownScopeMappingReferenceException(
                String.format("Cannot find realm role '%s' within realm '%s'", role, realm));
    }

    public static UnknownScopeMappingReferenceException clientRole(
            final String realm, final String clientId, final String role) {
        return new UnknownScopeMappingReferenceException(
                String.format("Cannot find client role '%s' for client '%s' within realm '%s'", role, clientId, realm));
    }
}
