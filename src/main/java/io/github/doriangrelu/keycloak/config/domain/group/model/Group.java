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

package io.github.doriangrelu.keycloak.config.domain.group.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A group of a realm, identified by its path ({@code /parent/child}). Import files nest subgroups; they are flattened
 * into one group per path, parents first.
 *
 * <p>In a desired group, a {@code null} field is not specified by the import file. Declared attributes replace the
 * current ones.
 * Declared role mappings are the exact role mappings of the group; in {@code full} managed mode, a group declared
 * without them loses its role mappings. Declared subgroups are the exact children of the group.
 *
 * @param realmRoles realm roles mapped to the group, or {@code null} when not specified
 * @param clientRoles client roles mapped to the group by client id, or {@code null} when not specified
 * @param subGroups names of the children, or {@code null} when not specified
 */
public record Group(
        String path,
        Map<String, List<String>> attributes,
        Set<String> realmRoles,
        Map<String, Set<String>> clientRoles,
        Set<String> subGroups,
        UnmodeledAttributes unmodeled)
        implements Resource<String> {

    public Group {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/") || path.length() < 2) {
            throw new IllegalArgumentException("Invalid group path: " + path);
        }
        attributes = attributes == null ? null : copyOfAttributes(attributes);
        realmRoles = realmRoles == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(realmRoles));
        clientRoles = clientRoles == null ? null : copyOfClientRoles(clientRoles);
        subGroups = subGroups == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(subGroups));
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    public static String childPath(final String parentPath, final String name) {
        return (parentPath == null ? "" : parentPath) + "/" + name;
    }

    @Override
    public String key() {
        return this.path;
    }

    /** The name: the remote state and protection rules work on group names. */
    @Override
    public String protectedName() {
        return name();
    }

    public String name() {
        return this.path.substring(this.path.lastIndexOf('/') + 1);
    }

    /** Path of the parent group, or {@code null} for a top-level group. */
    public String parentPath() {
        int separator = this.path.lastIndexOf('/');
        return separator == 0 ? null : this.path.substring(0, separator);
    }

    /**
     * This group once {@code desired} is applied on it. Declared attributes replace the current ones, and a group
     * declared without role mappings loses them: the files describe the whole group (ADR-007).
     */
    public Group mergedWith(final Group desired) {
        return new Group(
                this.path,
                desired.attributes != null ? desired.attributes : this.attributes,
                desired.realmRoles != null ? desired.realmRoles : Set.of(),
                desired.clientRoles != null ? desired.clientRoles : Map.of(),
                desired.subGroups != null ? desired.subGroups : this.subGroups,
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    /** The same group without its role mappings and children: what the group itself is updated with. */
    public Group withoutRolesAndChildren() {
        return new Group(this.path, this.attributes, null, null, null, this.unmodeled);
    }

    private static Map<String, List<String>> copyOfAttributes(final Map<String, List<String>> attributes) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        attributes.forEach((key, values) -> copy.put(key, values == null ? List.of() : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    /** A client declared with no role has no mapping: it is dropped, so that it compares equal to Keycloak. */
    private static Map<String, Set<String>> copyOfClientRoles(final Map<String, Set<String>> clientRoles) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        clientRoles.forEach((clientId, roles) -> {
            if (roles != null && !roles.isEmpty()) {
                copy.put(clientId, Collections.unmodifiableSet(new LinkedHashSet<>(roles)));
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
