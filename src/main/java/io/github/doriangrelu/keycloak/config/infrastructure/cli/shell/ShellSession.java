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

import io.github.doriangrelu.keycloak.config.application.definition.NoRealmOpenedException;
import java.util.Objects;
import java.util.Optional;

/** The realm the commands of a session work on. */
public final class ShellSession {

    private String openedRealm;

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
}
