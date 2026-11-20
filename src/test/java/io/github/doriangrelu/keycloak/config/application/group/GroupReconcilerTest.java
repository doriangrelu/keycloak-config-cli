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

package io.github.doriangrelu.keycloak.config.application.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.group.port.GroupGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule.Matching;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupReconcilerTest {

    static final class InMemoryGroups implements GroupGateway {
        final Map<String, Group> groups = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        void add(String path, Set<String> realmRoles) {
            this.groups.put(path, new Group(path, Map.of(), realmRoles, Map.of(), Set.of(), null));
        }

        @Override
        public List<Group> findAll(String realm) {
            return this.groups.values().stream()
                    .map(group -> new Group(
                            group.path(),
                            group.attributes(),
                            group.realmRoles(),
                            group.clientRoles(),
                            this.groups.keySet().stream()
                                    .filter(path -> group.path()
                                            .equals(new Group(path, null, null, null, null, null).parentPath()))
                                    .map(path -> path.substring(path.lastIndexOf('/') + 1))
                                    .collect(Collectors.toSet()),
                            null))
                    .toList();
        }

        @Override
        public void create(String realm, Group group) {
            this.log.add("create " + group.path());
            this.groups.put(group.path(), group);
        }

        @Override
        public void update(String realm, Group current, Group target) {
            this.log.add("update " + target.path());
            this.groups.put(target.path(), target);
        }

        @Override
        public void delete(String realm, String path) {
            if (this.groups.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"))) {
                this.log.add("delete " + path);
            }
        }
    }

    private InMemoryGroups keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryGroups();
        this.engine = new ReconciliationEngine(List.of(new GroupReconciler(this.keycloak)));
    }

    private static Group declared(String path, Set<String> realmRoles, Set<String> subGroups) {
        return new Group(path, null, realmRoles, null, subGroups, null);
    }

    private static RealmContext context() {
        return new RealmContext("test");
    }

    @Test
    void parentsAreCreatedBeforeChildren() {
        this.engine.apply(
                context(),
                new DesiredState()
                        .declare(
                                ResourceType.GROUP,
                                List.of(declared("/a", null, Set.of("b")), declared("/a/b", null, null))));

        assertThat(this.keycloak.log).containsExactly("create /a", "create /a/b");
    }

    @Test
    void removesUndeclaredRoleMappingsAndGroups() {
        this.keycloak.add("/a", Set.of("reader"));
        this.keycloak.add("/orphan", Set.of());
        RealmContext context = context();

        this.engine.apply(context, new DesiredState().declare(ResourceType.GROUP, List.of(declared("/a", null, null))));
        this.engine.prune(context);

        assertThat(this.keycloak.groups.get("/a").realmRoles()).isEmpty();
        assertThat(this.keycloak.groups).containsOnlyKeys("/a");
    }

    @Test
    void declaredSubGroupsReplaceTheChildrenButSpareProtectedOnes() {
        this.keycloak.add("/a", Set.of());
        this.keycloak.add("/a/kept", Set.of());
        this.keycloak.add("/a/removed", Set.of());
        this.keycloak.add("/a/protected", Set.of());
        RealmContext context = context();
        context.addProtections(List.of(new ProtectionRule(Matching.EQUALS, "protected")));

        this.engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.GROUP,
                                List.of(
                                        declared("/a", Set.of(), Set.of("kept")),
                                        declared("/a/kept", Set.of(), null))));

        assertThat(this.keycloak.groups).containsOnlyKeys("/a", "/a/kept", "/a/protected");
    }
}
