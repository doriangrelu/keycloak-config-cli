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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.SocketTimeoutException;

/**
 * Translation table of ADR-003, from Keycloak Admin client errors to {@link KeycloakAccessException}.
 *
 * <p>Used by the SDK adapters, and by the command line for the raw SDK errors still raised by legacy code.
 */
public final class KeycloakErrorTranslator {

    private KeycloakErrorTranslator() {}

    public static KeycloakAccessException translate(final WebApplicationException error) {
        Response response = error.getResponse();
        if (response == null) {
            return new KeycloakAccessException(
                    KeycloakFailure.UNEXPECTED, "Keycloak returned an invalid response", error);
        }
        return translate(response, error);
    }

    public static KeycloakAccessException translate(final ProcessingException error) {
        if (isReadTimeout(error)) {
            return new KeycloakAccessException(
                    KeycloakFailure.TIMEOUT, "Keycloak did not answer within the read timeout", error);
        }
        return new KeycloakAccessException(
                KeycloakFailure.UNAVAILABLE, "Keycloak is unreachable: " + error.getMessage(), error);
    }

    /**
     * @param cause the error that carried the response, or {@code null} when the response was received normally
     */
    public static KeycloakAccessException translate(final Response response, final Throwable cause) {
        int status = response.getStatus();
        String body = bodyOf(response);
        String message = body.isEmpty()
                ? "Keycloak responded with HTTP " + status
                : "Keycloak responded with HTTP " + status + ": " + body;
        return new KeycloakAccessException(failureOf(status), status, message, cause);
    }

    private static KeycloakFailure failureOf(final int status) {
        return switch (status) {
            case 400, 422 -> KeycloakFailure.REJECTED;
            case 401, 403 -> KeycloakFailure.UNAUTHORIZED;
            case 404 -> KeycloakFailure.NOT_FOUND;
            case 409 -> KeycloakFailure.CONFLICT;
            case 429, 503 -> KeycloakFailure.UNAVAILABLE;
            default -> KeycloakFailure.UNEXPECTED;
        };
    }

    private static boolean isReadTimeout(final Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Error body sent by Keycloak, usually {@code {"errorMessage": "..."}}. A response built locally (not read from
     * the network) has no input stream to read, so its entity is used instead.
     */
    private static String bodyOf(final Response response) {
        if (!response.hasEntity()) {
            return "";
        }
        try {
            return response.readEntity(String.class).trim();
        } catch (final IllegalStateException | ProcessingException unreadable) {
            return response.getEntity() instanceof String entity ? entity.trim() : "";
        }
    }
}
