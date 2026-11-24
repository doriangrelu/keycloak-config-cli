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
import java.util.Set;

/** The keys of the groups in the export format: nested {@code subGroups}, read as paths, parents first. */
final class GroupKeys {

    private GroupKeys() {}

    static List<Defined<GroupDefinition>> read(final List<TreeObject> groups, final String file) {
        List<Defined<GroupDefinition>> read = new ArrayList<>();
        flatten(groups, "", file, read);
        return read;
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
                    new Origin(file, group.line())));
            flatten(group.objects("subGroups"), path, file, read);
        }
    }
}
