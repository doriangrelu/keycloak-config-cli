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

package io.github.doriangrelu.keycloak.config.application.importation;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import java.util.Optional;

/**
 * An import file cannot be used: missing location, unreadable or unparsable file, invalid template, undefined variable
 * (ADR-005). Reported as an invalid configuration.
 *
 * <p>The message of a file that cannot be parsed quotes its content, as the parser tells it. A file rendered with the
 * values of variables may hold a secret there: such a failure also has a {@linkplain #safeMessage() safe message},
 * which names the file and where it fails, never its content.
 */
public final class InvalidImportException extends ApplicationException {

    private final String safeMessage;

    public InvalidImportException(final String message) {
        this(message, null, null);
    }

    public InvalidImportException(final String message, final Throwable cause) {
        this(message, cause, null);
    }

    /** @param safeMessage what the failure is, without any content of the file: the file and the place */
    public InvalidImportException(final String message, final Throwable cause, final String safeMessage) {
        super(message, cause);
        this.safeMessage = safeMessage;
    }

    /** The failure told without the content of the file, when it is known. */
    public Optional<String> safeMessage() {
        return Optional.ofNullable(this.safeMessage);
    }
}
