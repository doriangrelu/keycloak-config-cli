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

/** Qualified cause of a failed call to the Keycloak Admin API (ADR-003). */
public enum KeycloakFailure {
    /** Keycloak refused the submitted configuration (400, 422). */
    REJECTED,
    /** The configured account cannot perform the operation (401, 403). */
    UNAUTHORIZED,
    /** The resource does not exist (404). */
    NOT_FOUND,
    /** The resource conflicts with an existing one (409). */
    CONFLICT,
    /** Keycloak cannot be reached or is overloaded (connection failure, 429, 503). */
    UNAVAILABLE,
    /** Keycloak did not answer within the read timeout. */
    TIMEOUT,
    /** Any other error, including a response that cannot be interpreted. */
    UNEXPECTED
}
