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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;

/**
 * The Keycloak of a target refused the login, or an operation of an import for lack of rights (exit code 4, ADR-005).
 * The message never holds the secret.
 */
public final class KeycloakAuthenticationException extends ApplicationException {

    /**
     * @param status the HTTP status of the answer; 404 means the login realm, or the URL, is not the one of Keycloak
     * @param cause the client error: kept as a suppressed exception, never as the cause, because an interactive shell
     *     prints the deepest cause only
     */
    public KeycloakAuthenticationException(final Target target, final int status, final Throwable cause) {
        this(
                "Keycloak at " + target.url() + " " + whatHappened(status) + " to target '" + target.name() + "' ("
                        + accountOf(target) + "): HTTP " + status,
                cause);
    }

    private KeycloakAuthenticationException(final String message, final Throwable cause) {
        super(message, null);
        if (cause != null) {
            addSuppressed(cause);
        }
    }

    /**
     * Keycloak refused an operation of an import on a target it let log in: the account lacks the rights it needs.
     *
     * @param detail what Keycloak said of the refused operation, which never holds the secret
     * @param cause the client error, kept as a suppressed exception
     */
    public static KeycloakAuthenticationException refusedOperation(
            final Target target, final String detail, final Throwable cause) {
        return new KeycloakAuthenticationException(
                "Keycloak at " + target.url() + " refused an operation of the import on target '" + target.name()
                        + "' (missing rights?): " + detail,
                cause);
    }

    private static String whatHappened(final int status) {
        return status == 404 ? "has no such login realm or URL for the login" : "refused the login";
    }

    private static String accountOf(final Target target) {
        String client = "realm " + target.loginRealm() + ", client " + target.clientId();
        return target.grantType() == GrantType.PASSWORD
                ? client + ", user " + target.user().orElse("")
                : client;
    }
}
