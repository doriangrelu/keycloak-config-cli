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

import org.slf4j.Logger;

/**
 * Logs a failure once, where it is handled, and gives its exit code (ADR-005). Used only by the runners, the
 * single place where failures are caught.
 */
public final class FailureReporter {

    private FailureReporter() {}

    /**
     * @param operation what failed, for the log message ("import", "normalization")
     * @return the exit code to return to the caller of the command line
     */
    public static ExitCode report(final Logger logger, final String operation, final Exception failure) {
        ExitCode exitCode = ExitCodeResolver.resolve(failure);
        if (exitCode == ExitCode.UNEXPECTED_ERROR) {
            logger.error("Unexpected error during {}: {}", operation, failure.getMessage(), failure);
        } else {
            logger.error("{} failed ({}): {}", capitalize(operation), exitCode, failure.getMessage());
            logger.debug("Failure details", failure);
        }
        return exitCode;
    }

    private static String capitalize(final String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
