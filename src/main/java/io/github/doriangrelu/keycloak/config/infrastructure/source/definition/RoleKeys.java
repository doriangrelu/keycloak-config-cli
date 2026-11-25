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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The keys of the roles in the export format: {@code roles.realm} and {@code roles.client.<clientId>}. */
final class RoleKeys {

    private RoleKeys() {}

    static List<Defined<RoleDefinition>> read(final TreeObject roles, final String file) {
        List<Defined<RoleDefinition>> read = new ArrayList<>();
        for (TreeObject role : roles.objects("realm")) {
            read.add(defined(role, Optional.empty(), file));
        }
        roles.object("client")
                .ifPresent(clients -> clients.objectLists().forEach((clientId, clientRoles) -> {
                    for (TreeObject role : clientRoles) {
                        read.add(defined(role, Optional.of(TemplatedValue.of(clientId)), file));
                    }
                }));
        return read;
    }

    /**
     * Writes a role in {@code roles.realm}, or in {@code roles.client.<clientId>} for a client role: at the line of the
     * existing role, else at the end of the list.
     */
    static TreeObject write(final RoleDefinition role, final TreeObject roles, final Optional<Origin> existing) {
        TreeObject holder = role.clientId().isEmpty() ? roles : roles.objectOrCreate("client");
        String listKey = role.clientId().map(TemplatedValue::raw).orElse("realm");
        TreeObject object = existing.flatMap(at -> holder.objects(listKey).stream()
                        .filter(found -> found.isAt(at))
                        .findFirst())
                .orElseGet(() -> holder.append(listKey));
        object.setText("name", role.name());
        ClientKeys.setOrRemove(object, "description", role.description());
        return object;
    }

    private static Defined<RoleDefinition> defined(
            final TreeObject role, final Optional<TemplatedValue> clientId, final String file) {
        return new Defined<>(
                new RoleDefinition(
                        TemplatedValue.of(role.text("name").orElse("")),
                        clientId,
                        role.text("description").map(TemplatedValue::of)),
                new Origin(file, role.line(), role.column()));
    }
}
