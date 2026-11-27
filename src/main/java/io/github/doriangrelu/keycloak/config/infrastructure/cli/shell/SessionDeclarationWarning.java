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
package io.github.doriangrelu.keycloak.config.infrastructure.cli.shell;

import io.github.doriangrelu.keycloak.config.application.definition.DeclarationDeletions;
import io.github.doriangrelu.keycloak.config.application.definition.DeclarationWarning;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import java.util.Objects;
import java.util.function.Function;

/**
 * The warning of a first declaration in the session (ADR-010): connected, it lists what the next import would delete
 * on the target, from the values of the variables the session already knows; offline, it is the general warning.
 */
public final class SessionDeclarationWarning {

    private final Function<Questions, DeclarationDeletions> deletions;
    private final ShellSession session;

    /** @param deletions the use case, for the questions of a command; it asks none */
    public SessionDeclarationWarning(
            final Function<Questions, DeclarationDeletions> deletions, final ShellSession session) {
        this.deletions = Objects.requireNonNull(deletions, "deletions");
        this.session = Objects.requireNonNull(session, "session");
    }

    /** The warning for a command with these questions, on the connection of the session at this moment. */
    public DeclarationWarning of(final Questions questions) {
        return this.session
                .connection()
                .map(connection -> this.deletions.apply(questions).on(connection, this.session.variables()))
                .orElse(DeclarationWarning.OFFLINE);
    }
}
