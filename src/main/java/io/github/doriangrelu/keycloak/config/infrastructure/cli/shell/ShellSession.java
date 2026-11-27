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
import io.github.doriangrelu.keycloak.config.application.definition.NotConnectedException;
import io.github.doriangrelu.keycloak.config.application.definition.VariableValues;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The realm the commands of a session work on, the Keycloak it is connected to, and the values of the variables given
 * for them: those are forgotten when the realm or the target changes, and when the session ends.
 */
public final class ShellSession {

    private static final Logger logger = LoggerFactory.getLogger(ShellSession.class);

    private String openedRealm;
    private Connection connection;
    private final VariableValues variables = new VariableValues();
    private boolean pendingChanges;

    public Optional<String> openedRealm() {
        return Optional.ofNullable(this.openedRealm);
    }

    /** @throws NoRealmOpenedException when no realm is opened */
    public String requireOpenedRealm() {
        return openedRealm().orElseThrow(NoRealmOpenedException::new);
    }

    /** Opens the realm; the values of the variables are forgotten. */
    public void open(final String folder) {
        this.openedRealm = Objects.requireNonNull(folder, "folder");
        this.variables.forget();
    }

    /** The values of the variables given for the opened realm and the connected target, in memory only. */
    public VariableValues variables() {
        return this.variables;
    }

    /** @throws NotConnectedException when the session is connected to no target */
    public Connection requireConnection() {
        return connection().orElseThrow(NotConnectedException::new);
    }

    /** A dry run of the last command found changes to make: a script ends with code 10 (ADR-005). */
    public void notePendingChanges() {
        this.pendingChanges = true;
    }

    /** Whether a dry run found changes since the last call; the mark is then cleared. */
    public boolean takePendingChanges() {
        boolean pending = this.pendingChanges;
        this.pendingChanges = false;
        return pending;
    }

    /** The connection to the Keycloak of a target, when there is one. */
    public Optional<Connection> connection() {
        return Optional.ofNullable(this.connection);
    }

    /**
     * Makes this connection the current one, which is opened already: the previous one, if any, is closed only now, so
     * that a failed switch leaves it as it was. A previous connection that cannot be closed does not undo the switch:
     * the new one stays current, and what happened is told. The values of the variables are forgotten.
     *
     * @return a warning for the user, when the previous connection could not be closed
     */
    public Optional<String> connect(final Connection opened) {
        Connection previous = this.connection;
        this.connection = Objects.requireNonNull(opened, "opened");
        this.variables.forget();
        if (previous == null) {
            return Optional.empty();
        }
        try {
            previous.close();
        } catch (RuntimeException failure) {
            String name = previous.target().name();
            logger.warn("The connection to {} could not be closed", name, failure);
            return Optional.of("Warning: the previous connection to " + name + " could not be closed cleanly");
        }
        return Optional.empty();
    }

    /**
     * Closes the current connection, which is then forgotten with the values of the variables; the target it was to,
     * or none when there was none.
     */
    public Optional<Target> disconnect() {
        Connection current = this.connection;
        this.connection = null;
        this.variables.forget();
        if (current == null) {
            return Optional.empty();
        }
        current.close();
        return Optional.of(current.target());
    }

    /** Ends the session: the connection, if any, is closed, the values are forgotten. Closing again does nothing. */
    public void close() {
        disconnect();
    }
}
