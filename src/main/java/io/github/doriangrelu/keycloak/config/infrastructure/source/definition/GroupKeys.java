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
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The keys of the groups in the export format: nested {@code subGroups}, read as paths, parents first. */
final class GroupKeys {

    private GroupKeys() {}

    static List<Defined<GroupDefinition>> read(final List<TreeObject> groups, final String file) {
        List<Defined<GroupDefinition>> read = new ArrayList<>();
        flatten(groups, "", file, read);
        return read;
    }

    /**
     * Writes a group at its path: under {@code groups}, or in the {@code subGroups} of its parent, which must be
     * written already. Roles keep their order when the set of roles does not change.
     */
    static void write(final GroupDefinition group, final TreeObject root, final Optional<Origin> existing) {
        String[] names = group.path().substring(1).split("/");
        TreeObject holder = root;
        String listKey = "groups";
        for (int i = 0; i < names.length - 1; i++) {
            String parent = names[i];
            holder = holder.objects(listKey).stream()
                    .filter(found -> found.text("name").filter(parent::equals).isPresent())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The parent of group '" + group.path() + "' is not written: " + parent));
            listKey = "subGroups";
        }
        TreeObject parentHolder = holder;
        String parentListKey = listKey;
        TreeObject object = existing.flatMap(at -> parentHolder.objects(parentListKey).stream()
                        .filter(found -> found.isAt(at))
                        .findFirst())
                .orElseGet(() -> parentHolder.append(parentListKey));
        object.setText("name", group.name());
        writeRoles(object, "realmRoles", group.realmRoles(), object.texts("realmRoles"));
        if (group.clientRoles().isEmpty()) {
            object.remove("clientRoles");
            return;
        }
        Map<String, List<String>> current = object.textLists("clientRoles");
        TreeObject clientRoles = object.objectOrCreate("clientRoles");
        current.keySet().stream()
                .filter(client -> !group.clientRoles().containsKey(client))
                .forEach(clientRoles::remove);
        group.clientRoles()
                .forEach((client, roles) ->
                        writeRoles(clientRoles, client, roles, current.getOrDefault(client, List.of())));
    }

    private static void writeRoles(
            final TreeObject object, final String key, final Set<String> roles, final List<String> current) {
        if (roles.isEmpty()) {
            object.remove(key);
        } else if (!Set.copyOf(current).equals(roles)) {
            object.setTexts(key, roles.stream().sorted().map(TemplatedValue::of).toList());
        }
    }

    private static void flatten(
            final List<TreeObject> groups,
            final String parentPath,
            final String file,
            final List<Defined<GroupDefinition>> read) {
        for (TreeObject group : groups) {
            String name = group.text("name").orElse("");
            String path = parentPath + "/" + name;
            Map<String, Set<String>> clientRoles = new LinkedHashMap<>();
            group.textLists("clientRoles")
                    .forEach((client, roles) -> clientRoles.put(client, new LinkedHashSet<>(roles)));
            read.add(new Defined<>(
                    new GroupDefinition(
                            path, TemplatedValue.of(name), new LinkedHashSet<>(group.texts("realmRoles")), clientRoles),
                    new Origin(file, group.line(), group.column())));
            flatten(group.objects("subGroups"), path, file, read);
        }
    }
}
