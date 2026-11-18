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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ScopeMappingApi;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;

/** {@link ScopeMappingApi} on the Keycloak Admin client. */
public class ScopeMappingAdapter extends AbstractAdapter implements ScopeMappingApi {

    public ScopeMappingAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<ScopeMappingRepresentation> listRealmRoleScopeMappings(final String realm) {
        List<ScopeMappingRepresentation> mappings = export(realm).getScopeMappings();
        return mappings == null ? List.of() : mappings;
    }

    @Override
    public Map<String, List<ScopeMappingRepresentation>> listClientRoleScopeMappings(final String realm) {
        Map<String, List<ScopeMappingRepresentation>> mappings = export(realm).getClientScopeMappings();
        return mappings == null ? Map.of() : mappings;
    }

    @Override
    public void addRealmRoles(
            final String realm,
            final boolean clientScope,
            final String holderId,
            final List<RoleRepresentation> roles) {
        execute(() -> scopeMappings(realm, clientScope, holderId).realmLevel().add(roles));
    }

    @Override
    public void removeRealmRoles(
            final String realm,
            final boolean clientScope,
            final String holderId,
            final List<RoleRepresentation> roles) {
        execute(() -> scopeMappings(realm, clientScope, holderId).realmLevel().remove(roles));
    }

    @Override
    public void addClientRoles(
            final String realm,
            final boolean clientScope,
            final String holderId,
            final String roleClientUuid,
            final List<RoleRepresentation> roles) {
        execute(() -> scopeMappings(realm, clientScope, holderId)
                .clientLevel(roleClientUuid)
                .add(roles));
    }

    @Override
    public void removeClientRoles(
            final String realm,
            final boolean clientScope,
            final String holderId,
            final String roleClientUuid,
            final List<RoleRepresentation> roles) {
        execute(() -> scopeMappings(realm, clientScope, holderId)
                .clientLevel(roleClientUuid)
                .remove(roles));
    }

    private RealmRepresentation export(final String realm) {
        return execute(() -> keycloak().realm(realm).partialExport(true, true));
    }

    private RoleMappingResource scopeMappings(final String realm, final boolean clientScope, final String holderId) {
        return clientScope
                ? keycloak().realm(realm).clientScopes().get(holderId).getScopeMappings()
                : keycloak().realm(realm).clients().get(holderId).getScopeMappings();
    }
}
