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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ComponentApi;
import java.util.List;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ComponentRepresentation;

/** {@link ComponentApi} on the Keycloak Admin client. */
public class ComponentAdapter extends AbstractAdapter implements ComponentApi {

    public ComponentAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public String realmId(final String realm) {
        return execute(() -> keycloak().realm(realm).toRepresentation().getId());
    }

    @Override
    public List<ComponentRepresentation> listComponents(final String realm) {
        List<ComponentRepresentation> components =
                execute(() -> keycloak().realm(realm).components().query());
        return components == null ? List.of() : components;
    }

    @Override
    public String createComponent(final String realm, final ComponentRepresentation component) {
        return createAndGetId(() -> keycloak().realm(realm).components().add(component));
    }

    @Override
    public void updateComponent(final String realm, final ComponentRepresentation component) {
        execute(() -> keycloak()
                .realm(realm)
                .components()
                .component(component.getId())
                .update(component));
    }

    @Override
    public void deleteComponent(final String realm, final String id) {
        execute(() -> keycloak().realm(realm).components().component(id).remove());
    }

    @Override
    public void syncUsers(final String realm, final String userStorageId) {
        execute(() -> keycloak().realm(realm).userStorage().syncUsers(userStorageId, "triggerFullSync"));
    }
}
