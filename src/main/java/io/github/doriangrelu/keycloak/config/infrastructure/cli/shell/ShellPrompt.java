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

import java.util.Objects;

/** The prompt: {@code kcc}, then {@code :<folder>} of the opened realm and {@code @<target>} of the connection. */
public final class ShellPrompt {

    private final ShellSession session;

    public ShellPrompt(final ShellSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** The prompt, such as {@code kcc:shop@dev>}. */
    public String text() {
        return "kcc"
                + this.session.openedRealm().map(folder -> ":" + folder).orElse("")
                + this.session
                        .connection()
                        .map(connection -> "@" + connection.target().name())
                        .orElse("")
                + ">";
    }
}
