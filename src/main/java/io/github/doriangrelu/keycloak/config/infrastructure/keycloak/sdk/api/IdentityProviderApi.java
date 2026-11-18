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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api;

import java.util.List;
import java.util.Optional;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;

/** Identity provider operations of the Keycloak Admin API (ADR-003). Identity providers are addressed by alias. */
public interface IdentityProviderApi {

    List<IdentityProviderRepresentation> listIdentityProviders(String realm);

    Optional<IdentityProviderRepresentation> findIdentityProvider(String realm, String alias);

    void createIdentityProvider(String realm, IdentityProviderRepresentation identityProvider);

    void updateIdentityProvider(String realm, String alias, IdentityProviderRepresentation identityProvider);

    void deleteIdentityProvider(String realm, String alias);

    List<IdentityProviderMapperRepresentation> listMappers(String realm, String alias);

    void createMapper(String realm, String alias, IdentityProviderMapperRepresentation mapper);

    void updateMapper(String realm, String alias, String mapperId, IdentityProviderMapperRepresentation mapper);

    void deleteMapper(String realm, String alias, String mapperId);
}
