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

import io.github.doriangrelu.keycloak.config.application.group.port.GroupGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Groups and their role mappings, managed by {@code import.managed.group}. As in the legacy import:
 *
 * <ul>
 *   <li>deletions do not depend on the remote state, and spare protected groups;
 *   <li>a group declaring its subgroups loses the other children, whatever the managed mode;
 *   <li>groups are applied one by one, parents first, even with {@code import.parallel}.
 * </ul>
 */
public final class GroupReconciler implements Reconciler<String, Group> {

    private final GroupGateway groups;

    public GroupReconciler(final GroupGateway groups) {
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    @Override
    public ResourceType type() {
        return ResourceType.GROUP;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.REALM_ROLE, ResourceType.CLIENT_ROLE, ResourceType.CLIENT);
    }

    @Override
    public List<Group> read(final RealmContext context, final Collection<Group> desired) {
        return this.groups.findAll(context.realm());
    }

    @Override
    public Group merge(final RealmContext context, final Group current, final Group desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final Group resource) {
        this.groups.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final Group current, final Group target) {
        this.groups.update(context.realm(), current, target);
        if (target.subGroups() == null || current.subGroups() == null) {
            return;
        }
        Set<String> removedChildren = new LinkedHashSet<>(current.subGroups());
        removedChildren.removeAll(target.subGroups());
        removedChildren.stream()
                .filter(name -> !context.isProtected(name))
                .forEach(name -> this.groups.delete(context.realm(), Group.childPath(target.path(), name)));
    }

    @Override
    public void delete(final RealmContext context, final Group resource) {
        this.groups.delete(context.realm(), resource.path());
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final Group resource) {
        return false;
    }

    @Override
    public boolean appliesInParallel() {
        return false;
    }
}
