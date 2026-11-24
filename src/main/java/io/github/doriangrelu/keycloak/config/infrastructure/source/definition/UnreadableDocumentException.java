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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

/** A definition file is not valid YAML or JSON before rendering (ADR-008). */
public final class UnreadableDocumentException extends RuntimeException {

    private final int line;
    private final String reason;

    public UnreadableDocumentException(final int line, final String reason) {
        super("line " + line + ": " + reason);
        this.line = line;
        this.reason = reason;
    }

    /** The line of the problem, from 1. */
    public int line() {
        return this.line;
    }

    public String reason() {
        return this.reason;
    }
}
