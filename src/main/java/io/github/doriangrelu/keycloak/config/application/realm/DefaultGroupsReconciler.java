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

package io.github.doriangrelu.keycloak.config.application.realm;

import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Default groups of the realm, exact once declared. */
public final class DefaultGroupsReconciler implements Reconciler<String, DefaultGroups> {

    private final RealmGateway realms;

    public DefaultGroupsReconciler(final RealmGateway realms) {
        this.realms = Objects.requireNonNull(realms, "realms");
    }

    @Override
    public ResourceType type() {
        return ResourceType.DEFAULT_GROUP;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.GROUP);
    }

    @Override
    public List<DefaultGroups> read(final RealmContext context, final Collection<DefaultGroups> desired) {
        return List.of(this.realms.findDefaultGroups(context.realm()));
    }

    @Override
    public DefaultGroups merge(final RealmContext context, final DefaultGroups current, final DefaultGroups desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final DefaultGroups resource) {
        this.realms.addDefaultGroups(context.realm(), resource.paths());
    }

    @Override
    public void update(final RealmContext context, final DefaultGroups current, final DefaultGroups target) {
        Set<String> removed = new LinkedHashSet<>(current.paths());
        removed.removeAll(target.paths());
        if (!removed.isEmpty()) {
            this.realms.removeDefaultGroups(context.realm(), removed);
        }
        Set<String> added = new LinkedHashSet<>(target.paths());
        added.removeAll(current.paths());
        if (!added.isEmpty()) {
            this.realms.addDefaultGroups(context.realm(), added);
        }
    }

    @Override
    public void delete(final RealmContext context, final DefaultGroups resource) {
        throw new UnsupportedOperationException("Default groups are never deleted on their own");
    }

    @Override
    public String stateScope(final DefaultGroups resource) {
        return "default-groups";
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final DefaultGroups resource) {
        return false;
    }

    @Override
    public boolean prunes() {
        return false;
    }
}
