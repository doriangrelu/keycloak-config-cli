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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserProfileApi;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.userprofile.config.UPConfig;

/** {@link UserProfileApi} on the Keycloak Admin client. */
public class UserProfileAdapter extends AbstractAdapter implements UserProfileApi {

    public UserProfileAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public UPConfig getConfiguration(final String realm) {
        return execute(() -> keycloak().realm(realm).users().userProfile().getConfiguration());
    }

    @Override
    public void updateConfiguration(final String realm, final UPConfig configuration) {
        execute(() -> keycloak().realm(realm).users().userProfile().update(configuration));
    }
}
