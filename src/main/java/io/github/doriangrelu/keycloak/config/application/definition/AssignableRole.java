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

import java.util.Objects;
import java.util.Optional;

/**
 * A role a group can give: a realm role, or a role of a client.
 *
 * @param client the client id of a client role; empty for a realm role
 * @param builtIn whether Keycloak creates the role by itself, which the files do not define
 */
public record AssignableRole(Optional<String> client, String name, boolean builtIn) {

    public AssignableRole {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(name, "name");
    }

    /** The name of a realm role, {@code client/name} for a client role: as the commands give it. */
    public String label() {
        return this.client.map(id -> id + "/" + this.name).orElse(this.name);
    }

    /** Where the role comes from, and whether Keycloak creates it. */
    public String explanation() {
        String owner = this.client.map(id -> "client " + id).orElse("realm");
        return this.builtIn ? owner + " (Keycloak)" : owner;
    }
}
