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

import java.util.Objects;

/**
 * A call to the Keycloak Admin API failed. Raised only by the SDK adapters, which translate every SDK error into
 * this exception (ADR-003); the command line turns its {@link KeycloakFailure} into an exit code (ADR-005).
 */
public class KeycloakAccessException extends RuntimeException {

    /** No HTTP status: the call failed before Keycloak answered. */
    public static final int NO_STATUS = 0;

    private final KeycloakFailure failure;
    private final int status;

    public KeycloakAccessException(
            final KeycloakFailure failure, final int status, final String message, final Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
        this.status = status;
    }

    public KeycloakAccessException(final KeycloakFailure failure, final String message, final Throwable cause) {
        this(failure, NO_STATUS, message, cause);
    }

    public KeycloakAccessException(final KeycloakFailure failure, final String message) {
        this(failure, message, null);
    }

    public KeycloakFailure getFailure() {
        return this.failure;
    }

    /**
     * The HTTP status Keycloak answered with, or {@link #NO_STATUS}. Some Keycloak APIs answer {@code 501} when a
     * feature is disabled, which {@link KeycloakFailure#UNEXPECTED} does not tell apart from a server error.
     */
    public int getStatus() {
        return this.status;
    }
}
