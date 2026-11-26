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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.util.Optional;

/** How the shell authenticates against Keycloak. */
public enum GrantType {
    /** A user and a password. */
    PASSWORD("password"),
    /** A client and its secret. */
    CLIENT_CREDENTIALS("client_credentials");

    private final String optionName;

    GrantType(final String optionName) {
        this.optionName = optionName;
    }

    /** The grant type written as {@code password} or {@code client_credentials}. */
    public static Optional<GrantType> of(final String name) {
        for (GrantType type : values()) {
            if (type.optionName.equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public String optionName() {
        return optionName;
    }
}
