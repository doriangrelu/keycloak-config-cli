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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import java.util.Objects;

/**
 * The options of an import on a target, the same for every target: given by the command line, which reads them from
 * the defaults of the {@code import.*} properties.
 *
 * @param keycloak the options that concern Keycloak itself
 * @param syncUserFederation whether the user storages a file declares are synchronized before its users are applied
 */
public record TargetImportOptions(KeycloakImportOptions keycloak, boolean syncUserFederation) {

    public TargetImportOptions {
        Objects.requireNonNull(keycloak, "keycloak");
    }
}
