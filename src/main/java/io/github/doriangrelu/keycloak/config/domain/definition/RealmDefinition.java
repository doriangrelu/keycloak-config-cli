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
 * @param unreadableFiles the files that cannot be read before rendering; the realm is then read-only
 */
public record RealmDefinition(
        String folder,
        Optional<TemplatedValue> realm,
        List<Defined<ClientDefinition>> clients,
        List<Defined<RoleDefinition>> roles,
        List<Defined<GroupDefinition>> groups,
        Set<ElementType> declaredTypes,
        List<UnreadableFile> unreadableFiles) {

    public RealmDefinition {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(realm, "realm");
        clients = List.copyOf(clients);
        roles = List.copyOf(roles);
        groups = List.copyOf(groups);
        declaredTypes = Set.copyOf(declaredTypes);
        unreadableFiles = List.copyOf(unreadableFiles);
    }

    /** Whether the shell cannot know the whole realm, and so must not change it. */
    public boolean isReadOnly() {
        return !this.unreadableFiles.isEmpty();
    }

    /** The client with this id, as written. */
    public Optional<Defined<ClientDefinition>> client(final String clientId) {
        return this.clients.stream()
                .filter(client -> client.definition().clientId().raw().equals(clientId))
                .findFirst();
    }
}
