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

package io.github.doriangrelu.keycloak.config.application.realm.port;

import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import java.util.Optional;
import java.util.Set;

/** A realm in Keycloak: its settings, default client scopes and default groups. */
public interface RealmGateway {

    /** Settings of the realm; empty when it does not exist. */
    Optional<RealmSettings> findSettings(String realm);

    void create(RealmSettings settings);

    void update(RealmSettings settings);

    DefaultClientScopes findDefaultClientScopes(String realm, DefaultClientScopes.Kind kind);

    /** @throws io.github.doriangrelu.keycloak.config.application.realm.UnknownRealmReferenceException when a scope is missing */
    void addDefaultClientScopes(String realm, DefaultClientScopes.Kind kind, Set<String> scopes);

    void removeDefaultClientScopes(String realm, DefaultClientScopes.Kind kind, Set<String> scopes);

    DefaultGroups findDefaultGroups(String realm);

    /** @throws io.github.doriangrelu.keycloak.config.application.realm.UnknownRealmReferenceException when a group is missing */
    void addDefaultGroups(String realm, Set<String> paths);

    void removeDefaultGroups(String realm, Set<String> paths);
}
