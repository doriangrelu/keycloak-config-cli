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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RealmApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

/** {@link RealmApi} on the Keycloak Admin client. */
public class RealmAdapter extends AbstractAdapter implements RealmApi {

    public RealmAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    private RealmResource realm(final String realm) {
        return keycloak().realm(realm);
    }

    @Override
    public Optional<RealmRepresentation> findRealm(final String realm) {
        return find(() -> realm(realm).toRepresentation());
    }

    @Override
    public void createRealm(final RealmRepresentation realm) {
        execute(() -> keycloak().realms().create(realm));
        // The access token only grants the realms that existed when it was issued: refresh it for the new realm
        execute(() -> keycloak().tokenManager().refreshToken());
    }

    @Override
    public void updateRealm(final String realm, final RealmRepresentation representation) {
        execute(() -> realm(realm).update(representation));
    }

    @Override
    public List<ClientScopeRepresentation> listDefaultClientScopes(final String realm, final boolean optional) {
        List<ClientScopeRepresentation> scopes = execute(() -> optional
                ? realm(realm).getDefaultOptionalClientScopes()
                : realm(realm).getDefaultDefaultClientScopes());
        return scopes == null ? List.of() : scopes;
    }

    @Override
    public void addDefaultClientScope(final String realm, final boolean optional, final String clientScopeId) {
        execute(() -> {
            if (optional) {
                realm(realm).addDefaultOptionalClientScope(clientScopeId);
            } else {
                realm(realm).addDefaultDefaultClientScope(clientScopeId);
            }
        });
    }

    @Override
    public void removeDefaultClientScope(final String realm, final boolean optional, final String clientScopeId) {
        execute(() -> {
            if (optional) {
                realm(realm).removeDefaultOptionalClientScope(clientScopeId);
            } else {
                realm(realm).removeDefaultDefaultClientScope(clientScopeId);
            }
        });
    }

    @Override
    public void addDefaultGroup(final String realm, final String groupId) {
        execute(() -> realm(realm).addDefaultGroup(groupId));
    }

    @Override
    public void removeDefaultGroup(final String realm, final String groupId) {
        execute(() -> realm(realm).removeDefaultGroup(groupId));
    }
}
