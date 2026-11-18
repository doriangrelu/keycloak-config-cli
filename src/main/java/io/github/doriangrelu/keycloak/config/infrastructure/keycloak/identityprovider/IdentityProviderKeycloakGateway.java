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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider;

import io.github.doriangrelu.keycloak.config.application.identityprovider.port.IdentityProviderGateway;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProvider;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProviderMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link IdentityProviderGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class IdentityProviderKeycloakGateway implements IdentityProviderGateway {

    private final IdentityProviderApi identityProviderApi;
    private final IdentityProviderKeycloakMapper mapper;

    public IdentityProviderKeycloakGateway(
            final IdentityProviderApi identityProviderApi, final IdentityProviderKeycloakMapper mapper) {
        this.identityProviderApi = identityProviderApi;
        this.mapper = mapper;
    }

    @Override
    public List<IdentityProvider> findAll(final String realm) {
        return this.identityProviderApi.listIdentityProviders(realm).stream()
                .map(this.mapper::toIdentityProvider)
                .toList();
    }

    @Override
    public void create(final String realm, final IdentityProvider identityProvider) {
        this.identityProviderApi.createIdentityProvider(realm, this.mapper.toRepresentation(identityProvider));
    }

    @Override
    public void update(final String realm, final IdentityProvider identityProvider) {
        IdentityProviderRepresentation representation = this.mapper.toRepresentation(identityProvider);
        this.identityProviderApi
                .findIdentityProvider(realm, identityProvider.alias())
                .ifPresent(current -> representation.setInternalId(current.getInternalId()));
        this.identityProviderApi.updateIdentityProvider(realm, identityProvider.alias(), representation);
    }

    @Override
    public void delete(final String realm, final String alias) {
        this.identityProviderApi.deleteIdentityProvider(realm, alias);
    }

    @Override
    public List<IdentityProviderMapper> findAllMappers(final String realm) {
        return this.identityProviderApi.listIdentityProviders(realm).stream()
                .flatMap(identityProvider ->
                        this.identityProviderApi.listMappers(realm, identityProvider.getAlias()).stream())
                .map(this.mapper::toMapper)
                .toList();
    }

    @Override
    public void createMapper(final String realm, final IdentityProviderMapper mapper) {
        this.identityProviderApi.createMapper(
                realm, mapper.key().identityProviderAlias(), this.mapper.toRepresentation(mapper));
    }

    @Override
    public void updateMapper(final String realm, final IdentityProviderMapper mapper) {
        String alias = mapper.key().identityProviderAlias();
        String mapperId = idOf(realm, mapper.key())
                .orElseThrow(() -> new IllegalStateException("Identity provider mapper '"
                        + mapper.key().name() + "' of '" + alias + "' not found in realm " + realm));
        IdentityProviderMapperRepresentation representation = this.mapper.toRepresentation(mapper);
        representation.setId(mapperId);
        this.identityProviderApi.updateMapper(realm, alias, mapperId, representation);
    }

    @Override
    public void deleteMapper(final String realm, final IdentityProviderMapper.Key key) {
        if (this.identityProviderApi
                .findIdentityProvider(realm, key.identityProviderAlias())
                .isEmpty()) {
            return;
        }
        idOf(realm, key).ifPresent(id -> this.identityProviderApi.deleteMapper(realm, key.identityProviderAlias(), id));
    }

    private Optional<String> idOf(final String realm, final IdentityProviderMapper.Key key) {
        return this.identityProviderApi.listMappers(realm, key.identityProviderAlias()).stream()
                .filter(mapper -> Objects.equals(mapper.getName(), key.name()))
                .map(IdentityProviderMapperRepresentation::getId)
                .findFirst();
    }
}
