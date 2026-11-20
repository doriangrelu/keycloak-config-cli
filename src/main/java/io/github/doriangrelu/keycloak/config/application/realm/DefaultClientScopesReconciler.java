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
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default and optional client scopes of the realm, each kind exact once declared. Removals come first, so that a
 * scope can move from one kind to the other.
 */
public final class DefaultClientScopesReconciler implements Reconciler<DefaultClientScopes.Kind, DefaultClientScopes> {

    private final RealmGateway realms;

    public DefaultClientScopesReconciler(final RealmGateway realms) {
        this.realms = Objects.requireNonNull(realms, "realms");
    }

    @Override
    public ResourceType type() {
        return ResourceType.DEFAULT_CLIENT_SCOPE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.CLIENT_SCOPE);
    }

    @Override
    public List<DefaultClientScopes> read(final RealmContext context, final Collection<DefaultClientScopes> desired) {
        return desired.stream()
                .map(declared -> this.realms.findDefaultClientScopes(context.realm(), declared.kind()))
                .toList();
    }

    @Override
    public DefaultClientScopes merge(
            final RealmContext context, final DefaultClientScopes current, final DefaultClientScopes desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final DefaultClientScopes resource) {
        update(context, new DefaultClientScopes(resource.kind(), Set.of()), resource);
    }

    @Override
    public void update(
            final RealmContext context, final DefaultClientScopes current, final DefaultClientScopes target) {
        Set<String> removed = new LinkedHashSet<>(current.scopes());
        removed.removeAll(target.scopes());
        if (!removed.isEmpty()) {
            this.realms.removeDefaultClientScopes(context.realm(), target.kind(), removed);
        }
        Set<String> added = new LinkedHashSet<>(target.scopes());
        added.removeAll(current.scopes());
        if (!added.isEmpty()) {
            leaveOtherKind(context.realm(), target.kind(), added);
            this.realms.addDefaultClientScopes(context.realm(), target.kind(), added);
        }
    }

    /** A realm gives a scope as default or as optional, not both: a scope changing kind first leaves the other. */
    private void leaveOtherKind(final String realm, final DefaultClientScopes.Kind kind, final Set<String> added) {
        DefaultClientScopes.Kind other = kind == DefaultClientScopes.Kind.DEFAULT
                ? DefaultClientScopes.Kind.OPTIONAL
                : DefaultClientScopes.Kind.DEFAULT;
        Set<String> moving = new LinkedHashSet<>(
                this.realms.findDefaultClientScopes(realm, other).scopes());
        moving.retainAll(added);
        if (!moving.isEmpty()) {
            this.realms.removeDefaultClientScopes(realm, other, moving);
        }
    }

    @Override
    public void delete(final RealmContext context, final DefaultClientScopes resource) {
        throw new UnsupportedOperationException("Default client scopes are never deleted on their own");
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final DefaultClientScopes resource) {
        return false;
    }

    @Override
    public boolean prunes() {
        return false;
    }

    /** Both kinds share the same scopes: one kind at a time. */
    @Override
    public boolean appliesInParallel() {
        return false;
    }
}
