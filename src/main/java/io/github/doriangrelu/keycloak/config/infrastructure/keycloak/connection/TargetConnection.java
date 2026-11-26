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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import org.springframework.context.ConfigurableApplicationContext;

/** A connection held by the Spring context of its target: closing the connection closes the context. */
final class TargetConnection implements Connection {

    private final Target target;
    private final String keycloakVersion;
    private final ConfigurableApplicationContext context;

    TargetConnection(final Target target, final String keycloakVersion, final ConfigurableApplicationContext context) {
        this.target = target;
        this.keycloakVersion = keycloakVersion;
        this.context = context;
    }

    @Override
    public Target target() {
        return this.target;
    }

    @Override
    public String keycloakVersion() {
        return this.keycloakVersion;
    }

    /** The Spring context of the target, where the Keycloak client and the import live. */
    ConfigurableApplicationContext context() {
        return this.context;
    }

    @Override
    public void close() {
        this.context.close();
    }
}
