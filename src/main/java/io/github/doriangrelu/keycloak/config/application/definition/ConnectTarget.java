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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.definition.port.Targets;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Opens a connection to a named target of kcc-targets.yaml (ADR-010). */
public final class ConnectTarget {

    private final Targets targets;
    private final KeycloakConnector connector;

    public ConnectTarget(final Targets targets, final KeycloakConnector connector) {
        this.targets = Objects.requireNonNull(targets);
        this.connector = Objects.requireNonNull(connector);
    }

    /** The targets the shell can connect to. */
    public List<Target> targets() {
        return targets.all();
    }

    /**
     * Connects to the target with this name.
     *
     * @param secretOf asks for the password or the client secret of the target, once the target is known
     * @throws UnknownTargetException when no target has this name
     */
    public Connection connect(final String name, final Function<Target, String> secretOf) {
        Target target = targets.find(name).orElseThrow(() -> new UnknownTargetException(name, targets.all()));
        return connector.connect(target, secretOf.apply(target));
    }
}
