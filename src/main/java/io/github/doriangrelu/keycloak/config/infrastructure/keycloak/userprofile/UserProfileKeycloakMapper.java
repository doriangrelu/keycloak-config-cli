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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.userprofile;

import io.github.doriangrelu.keycloak.config.domain.userprofile.model.UserProfile;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Set;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.springframework.stereotype.Component;

/** Conversions between the Keycloak user profile configuration and its business object (ADR-006). */
@Component
public class UserProfileKeycloakMapper {

    private final UnmodeledAttributesConverter unmodeledAttributes;

    public UserProfileKeycloakMapper(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /** @param configuration a configuration read from Keycloak or declared by an import file */
    public UserProfile toUserProfile(final UPConfig configuration) {
        if (configuration == null) {
            return UserProfile.byDefault();
        }
        return new UserProfile(this.unmodeledAttributes.extract(configuration, Set.of()));
    }

    public UPConfig toConfiguration(final UserProfile userProfile) {
        if (userProfile.isDefault()) {
            return null;
        }
        UPConfig configuration = new UPConfig();
        this.unmodeledAttributes.apply(userProfile.configuration(), configuration);
        return configuration;
    }
}
