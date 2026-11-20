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

package io.github.doriangrelu.keycloak.config.domain.authenticationflow.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The configuration of an authenticator, identified by its alias. It only exists through an execution of a flow,
 * which creates it: an import can change it, never create it.
 *
 * @param inUse whether an execution refers to it; an unused configuration is garbage
 */
public record AuthenticatorConfig(String alias, Map<String, String> config, boolean inUse) implements Resource<String> {

    public AuthenticatorConfig {
        Objects.requireNonNull(alias, "alias");
        config = config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }

    @Override
    public String key() {
        return this.alias;
    }

    @Override
    public String protectedName() {
        return this.alias;
    }

    /** This configuration once {@code desired} is applied on it: the declared config replaces the current one. */
    public AuthenticatorConfig mergedWith(final AuthenticatorConfig desired) {
        return new AuthenticatorConfig(this.alias, desired.config, this.inUse);
    }
}
