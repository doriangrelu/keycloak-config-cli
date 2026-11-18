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

import io.github.doriangrelu.keycloak.config.application.userprofile.port.UserProfileGateway;
import io.github.doriangrelu.keycloak.config.domain.userprofile.model.UserProfile;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserProfileApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link UserProfileGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class UserProfileKeycloakGateway implements UserProfileGateway {

    private final UserProfileApi userProfileApi;
    private final UserProfileKeycloakMapper mapper;

    public UserProfileKeycloakGateway(final UserProfileApi userProfileApi, final UserProfileKeycloakMapper mapper) {
        this.userProfileApi = userProfileApi;
        this.mapper = mapper;
    }

    @Override
    public UserProfile find(final String realm) {
        return this.mapper.toUserProfile(this.userProfileApi.getConfiguration(realm));
    }

    @Override
    public void save(final String realm, final UserProfile userProfile) {
        this.userProfileApi.updateConfiguration(realm, this.mapper.toConfiguration(userProfile));
    }
}
