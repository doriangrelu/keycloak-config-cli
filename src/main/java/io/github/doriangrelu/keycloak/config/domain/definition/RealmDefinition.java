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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The definition of a realm: every file of its folder, read before rendering (ADR-008).
 *
 * @param folder the folder of the realm, under the root
 * @param realm the {@code realm} key of its files, which may hold variables
 * @param declaredTypes the types at least one file declares: the import cleans them (ADR-007)
 * @param declaresRealmRoles whether a file declares the realm roles ({@code roles.realm}): the import cleans them
 * @param declaredRoleClients the clients whose roles a file declares ({@code roles.client.<clientId>}): the import
 *     cleans the roles of each of them, and only of them
 * @param unreadableFiles the files that cannot be read before rendering; the realm is then read-only
 */
public record RealmDefinition(
        String folder,
        Optional<TemplatedValue> realm,
        List<Defined<ClientDefinition>> clients,
        List<Defined<RoleDefinition>> roles,
        List<Defined<GroupDefinition>> groups,
        Set<ElementType> declaredTypes,
        boolean declaresRealmRoles,
        Set<String> declaredRoleClients,
        List<UnreadableFile> unreadableFiles) {

    /** A realm whose role declarations are only known by type. */
    public RealmDefinition(
            final String folder,
            final Optional<TemplatedValue> realm,
            final List<Defined<ClientDefinition>> clients,
            final List<Defined<RoleDefinition>> roles,
            final List<Defined<GroupDefinition>> groups,
            final Set<ElementType> declaredTypes,
            final List<UnreadableFile> unreadableFiles) {
        this(
                folder,
                realm,
                clients,
                roles,
                groups,
                declaredTypes,
                declaredTypes.contains(ElementType.ROLE),
                Set.of(),
                unreadableFiles);
    }

    public RealmDefinition {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(realm, "realm");
        clients = List.copyOf(clients);
        roles = List.copyOf(roles);
        groups = List.copyOf(groups);
        declaredTypes = Set.copyOf(declaredTypes);
        declaredRoleClients = Set.copyOf(declaredRoleClients);
        unreadableFiles = List.copyOf(unreadableFiles);
    }

    /** Whether the shell cannot know the whole realm, and so must not change it. */
    public boolean isReadOnly() {
        return !this.unreadableFiles.isEmpty();
    }

    /**
     * Whether the files already declare the elements cleaned with this one at import: its type, or for a role, the
     * realm roles or the roles of its client (ADR-007).
     */
    public boolean declares(final ElementDefinition element) {
        if (element instanceof RoleDefinition role) {
            return role.clientId()
                    .map(clientId -> this.declaredRoleClients.contains(clientId.raw()))
                    .orElse(this.declaresRealmRoles);
        }
        return this.declaredTypes.contains(element.type());
    }

    /** The client with this id, as written. */
    public Optional<Defined<ClientDefinition>> client(final String clientId) {
        return this.clients.stream()
                .filter(client -> client.definition().clientId().raw().equals(clientId))
                .findFirst();
    }
}
