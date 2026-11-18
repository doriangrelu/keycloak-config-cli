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

package io.github.doriangrelu.keycloak.config.application.role;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;

/** A composite role includes a role that does not exist in the realm. */
public class UnknownRoleException extends ApplicationException {

    public UnknownRoleException(final String realm, final RoleKey composite, final RoleKey missing) {
        super(String.format(
                "Error adding composite roles to %s role '%s': Cannot find %s role '%s' within realm '%s'",
                composite.isClientRole() ? "client" : "realm",
                composite.name(),
                missing.isClientRole() ? "client" : "realm",
                missing.name(),
                realm));
    }
}
