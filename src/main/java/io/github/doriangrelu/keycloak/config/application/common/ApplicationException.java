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

package io.github.doriangrelu.keycloak.config.application.common;

/**
 * A use case cannot proceed with the given input: invalid import file, dependency cycle... (ADR-005). Reported as
 * an invalid configuration unless a subclass has a dedicated exit code.
 */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(final String message) {
        super(message);
    }

    protected ApplicationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
