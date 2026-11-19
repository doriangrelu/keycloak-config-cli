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

package io.github.doriangrelu.keycloak.config.domain.clientauthorization.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;

/**
 * The authorization services of a client ({@code authorizationSettings}): its settings, resources, scopes and
 * policies. Identified like the client: its client id, or {@code name:<name>} for a client only known by its name.
 *
 * <p>The settings are not modeled. Declared resources and policies may name Keycloak objects through placeholders,
 * such as {@code client.resource.$my-client}, only resolved against Keycloak when they are applied: the settings are
 * applied on every run, and each part is only written when it changed.
 */
public record ClientAuthorization(String clientId, String name, UnmodeledAttributes settings)
        implements Resource<String> {

    private static final String NAME_PREFIX = "name:";

    public ClientAuthorization {
        if (clientId == null && name == null) {
            throw new IllegalArgumentException("A client needs a client id or a name");
        }
        settings = settings == null ? UnmodeledAttributes.none() : settings;
    }

    @Override
    public String key() {
        return this.clientId != null ? this.clientId : NAME_PREFIX + this.name;
    }

    @Override
    public String stateName() {
        return key();
    }
}
