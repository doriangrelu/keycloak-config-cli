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

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.NoRealmOpenedException;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.util.Objects;
import java.util.Optional;

/** The realm the commands of a session work on, and the Keycloak it is connected to. */
public final class ShellSession {

    private String openedRealm;
    private Connection connection;

    public Optional<String> openedRealm() {
        return Optional.ofNullable(this.openedRealm);
    }

    /** @throws NoRealmOpenedException when no realm is opened */
    public String requireOpenedRealm() {
        return openedRealm().orElseThrow(NoRealmOpenedException::new);
    }

    public void open(final String folder) {
        this.openedRealm = Objects.requireNonNull(folder, "folder");
    }

    /** The connection to the Keycloak of a target, when there is one. */
    public Optional<Connection> connection() {
        return Optional.ofNullable(this.connection);
    }

    /**
     * Makes this connection the current one, which is opened already: the previous one, if any, is closed only now, so
     * that a failed switch leaves it as it was.
     */
    public void connect(final Connection opened) {
        Connection previous = this.connection;
        this.connection = Objects.requireNonNull(opened, "opened");
        if (previous != null) {
            previous.close();
        }
    }

    /** Closes the current connection, which is then forgotten; the target it was to, or none when there was none. */
    public Optional<Target> disconnect() {
        Connection current = this.connection;
        this.connection = null;
        if (current == null) {
            return Optional.empty();
        }
        current.close();
        return Optional.of(current.target());
    }

    /** Ends the session: the connection, if any, is closed. Closing again does nothing. */
    public void close() {
        disconnect();
    }
}
