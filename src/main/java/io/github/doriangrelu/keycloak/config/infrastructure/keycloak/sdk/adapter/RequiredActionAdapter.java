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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RequiredActionApi;
import java.util.List;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderSimpleRepresentation;

/** {@link RequiredActionApi} on the Keycloak Admin client. */
public class RequiredActionAdapter extends AbstractAdapter implements RequiredActionApi {

    public RequiredActionAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<RequiredActionProviderRepresentation> listRequiredActions(final String realm) {
        return execute(() -> flows(realm).getRequiredActions());
    }

    @Override
    public void registerRequiredAction(final String realm, final String providerId, final String name) {
        RequiredActionProviderSimpleRepresentation registration = new RequiredActionProviderSimpleRepresentation();
        registration.setProviderId(providerId);
        registration.setName(name);
        execute(() -> flows(realm).registerRequiredAction(registration));
    }

    @Override
    public void updateRequiredAction(
            final String realm, final String alias, final RequiredActionProviderRepresentation requiredAction) {
        execute(() -> flows(realm).updateRequiredAction(alias, requiredAction));
    }

    @Override
    public void removeRequiredAction(final String realm, final String alias) {
        execute(() -> flows(realm).removeRequiredAction(alias));
    }

    private AuthenticationManagementResource flows(final String realm) {
        return keycloak().realm(realm).flows();
    }
}
