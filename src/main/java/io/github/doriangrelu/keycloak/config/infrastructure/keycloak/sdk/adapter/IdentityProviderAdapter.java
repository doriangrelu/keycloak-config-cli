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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;

/** {@link IdentityProviderApi} on the Keycloak Admin client. */
public class IdentityProviderAdapter extends AbstractAdapter implements IdentityProviderApi {

    public IdentityProviderAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<IdentityProviderRepresentation> listIdentityProviders(final String realm) {
        return execute(() -> keycloak().realm(realm).identityProviders().findAll());
    }

    @Override
    public Optional<IdentityProviderRepresentation> findIdentityProvider(final String realm, final String alias) {
        return find(() -> identityProvider(realm, alias).toRepresentation());
    }

    @Override
    public void createIdentityProvider(final String realm, final IdentityProviderRepresentation identityProvider) {
        createAndGetId(() -> keycloak().realm(realm).identityProviders().create(identityProvider));
    }

    @Override
    public void updateIdentityProvider(
            final String realm, final String alias, final IdentityProviderRepresentation identityProvider) {
        execute(() -> identityProvider(realm, alias).update(identityProvider));
    }

    @Override
    public void deleteIdentityProvider(final String realm, final String alias) {
        execute(() -> identityProvider(realm, alias).remove());
    }

    @Override
    public List<IdentityProviderMapperRepresentation> listMappers(final String realm, final String alias) {
        List<IdentityProviderMapperRepresentation> mappers =
                execute(() -> identityProvider(realm, alias).getMappers());
        return mappers == null ? List.of() : mappers;
    }

    @Override
    public void createMapper(
            final String realm, final String alias, final IdentityProviderMapperRepresentation mapper) {
        createAndGetId(() -> identityProvider(realm, alias).addMapper(mapper));
    }

    @Override
    public void updateMapper(
            final String realm,
            final String alias,
            final String mapperId,
            final IdentityProviderMapperRepresentation mapper) {
        execute(() -> identityProvider(realm, alias).update(mapperId, mapper));
    }

    @Override
    public void deleteMapper(final String realm, final String alias, final String mapperId) {
        execute(() -> identityProvider(realm, alias).delete(mapperId));
    }

    private IdentityProviderResource identityProvider(final String realm, final String alias) {
        return keycloak().realm(realm).identityProviders().get(alias);
    }
}
