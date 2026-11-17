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

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

/**
 * Exit codes of the command line (ADR-005). They are a public interface: values only change with a major version.
 */
public enum ExitCode {
    SUCCESS(0),
    UNEXPECTED_ERROR(1),
    /** Import files, properties or a business rule are invalid: fix the configuration. */
    INVALID_CONFIGURATION(2),
    /** Keycloak cannot be reached or did not answer in time: try again later. */
    KEYCLOAK_UNAVAILABLE(3),
    /** Keycloak refused an operation (rights, rejected or conflicting configuration). */
    KEYCLOAK_REFUSED(4),
    /** An already applied import changed while {@code import.behaviors.checksum-changed=fail}. */
    CHECKSUM_CHANGED(5),
    /** Dry run only: changes are pending. */
    PENDING_CHANGES(10);

    private final int code;

    ExitCode(final int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }
}
