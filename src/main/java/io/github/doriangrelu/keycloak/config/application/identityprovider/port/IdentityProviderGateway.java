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

package io.github.doriangrelu.keycloak.config.application.identityprovider.port;

import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProvider;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProviderMapper;
import java.util.List;

/** Identity providers of a realm in Keycloak, and their mappers. */
public interface IdentityProviderGateway {

    List<IdentityProvider> findAll(String realm);

    void create(String realm, IdentityProvider identityProvider);

    void update(String realm, IdentityProvider identityProvider);

    void delete(String realm, String alias);

    /** Mappers of every identity provider of the realm. */
    List<IdentityProviderMapper> findAllMappers(String realm);

    void createMapper(String realm, IdentityProviderMapper mapper);

    void updateMapper(String realm, IdentityProviderMapper mapper);

    /** Deletes the mapper, if it still exists: it disappears with its identity provider. */
    void deleteMapper(String realm, IdentityProviderMapper.Key key);
}
