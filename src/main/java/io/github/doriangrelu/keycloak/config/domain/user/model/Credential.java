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

package io.github.doriangrelu.keycloak.config.domain.user.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;

/**
 * A credential declared for a user. Only its type and label carry rules; the value and the other fields are carried
 * as is.
 */
public record Credential(String type, String userLabel, UnmodeledAttributes unmodeled) {

    public static final String PASSWORD = "password";

    /** Label of a credential set when the user is created, and never changed afterwards. */
    public static final String INITIAL = "initial";

    public Credential {
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    public boolean isPassword() {
        return PASSWORD.equals(this.type);
    }

    public boolean isInitial() {
        return INITIAL.equals(this.userLabel);
    }
}
