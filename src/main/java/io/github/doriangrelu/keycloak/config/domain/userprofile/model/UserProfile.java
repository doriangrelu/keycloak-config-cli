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

package io.github.doriangrelu.keycloak.config.domain.userprofile.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;

/**
 * The user profile configuration of a realm: one per realm. Its content is not modeled and is compared as a whole.
 *
 * @param configuration the configuration, {@code null} for the default configuration of Keycloak
 */
public record UserProfile(UnmodeledAttributes configuration) implements Resource<String> {

    /** Key of the only user profile of a realm. */
    public static final String KEY = "user-profile";

    public static UserProfile byDefault() {
        return new UserProfile(null);
    }

    public boolean isDefault() {
        return this.configuration == null;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String protectedName() {
        return KEY;
    }
}
