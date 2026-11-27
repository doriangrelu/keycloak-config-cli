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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import io.github.doriangrelu.keycloak.config.application.definition.KeycloakAuthenticationException;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

/**
 * The failures of the Keycloak of a target, told with the target (ADR-005): an unreachable Keycloak gives exit code
 * {@code 3}, a refused account {@code 4}. The client error is kept as a suppressed exception, never as the cause, and
 * no message holds the secret.
 */
final class KeycloakFailures {

    private KeycloakFailures() {}

    /**
     * A failure of the first calls: the HTTP client could not reach Keycloak, or Keycloak answered the login with an
     * error. Any other failure is left as it is.
     */
    static RuntimeException atConnection(final Target target, final RuntimeException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ProcessingException unreachable) {
                return new KeycloakUnreachableException(target, reasonOf(unreachable), failure);
            }
            if (current instanceof WebApplicationException answered && answered.getResponse() != null) {
                int status = answered.getResponse().getStatus();
                return switch (status) {
                    case 400, 401, 403, 404 -> new KeycloakAuthenticationException(target, status, failure);
                    case 429, 502, 503, 504 -> new KeycloakUnreachableException(target, "HTTP " + status, failure);
                    default -> failure;
                };
            }
        }
        return failure;
    }

    /**
     * A failure of an import on a connected target: Keycloak could not be reached, did not answer in time, or refused
     * the account ({@code 401}, {@code 403}). A configuration refused by Keycloak ({@code 400}, {@code 404},
     * {@code 409}...), an invalid file and any other failure are left as they are.
     */
    static RuntimeException duringImport(final Target target, final RuntimeException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof KeycloakAccessException access) {
                return switch (access.getFailure()) {
                    case UNAVAILABLE, TIMEOUT -> new KeycloakUnreachableException(target, reasonOf(access), failure);
                    case UNAUTHORIZED -> new KeycloakAuthenticationException(target, access.getStatus(), failure);
                    default -> failure;
                };
            }
            if (current instanceof ProcessingException unreachable) {
                return new KeycloakUnreachableException(target, reasonOf(unreachable), failure);
            }
            if (current instanceof WebApplicationException answered && answered.getResponse() != null) {
                int status = answered.getResponse().getStatus();
                return switch (status) {
                    case 401, 403 -> new KeycloakAuthenticationException(target, status, failure);
                    case 429, 502, 503, 504 -> new KeycloakUnreachableException(target, "HTTP " + status, failure);
                    default -> failure;
                };
            }
        }
        return failure;
    }

    /** The deepest message of the HTTP client error: "Connection refused", "Connect timed out", the unknown host. */
    private static String reasonOf(final Throwable error) {
        String reason = error.getMessage();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                reason = current.getClass().getSimpleName() + ": " + current.getMessage();
            }
        }
        return reason;
    }
}
