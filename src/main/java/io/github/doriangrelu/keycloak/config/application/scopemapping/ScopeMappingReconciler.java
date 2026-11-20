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

package io.github.doriangrelu.keycloak.config.application.scopemapping;

import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.application.scopemapping.port.ScopeMappingGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.scopemapping.model.ScopeMapping;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scope mappings of realm roles ({@link ResourceType#SCOPE_MAPPING}, {@code import.managed.scope-mapping}) or of
 * client roles ({@link ResourceType#CLIENT_SCOPE_MAPPING}, {@code import.managed.client-scope-mapping}). As before,
 * deletions do not depend on the remote state: in {@code full} mode, a mapping missing from the import loses all its
 * roles, and a declared mapping loses the roles it does not declare.
 */
public final class ScopeMappingReconciler implements Reconciler<ScopeMapping.Key, ScopeMapping> {

    private final ResourceType type;
    private final ScopeMappingGateway scopeMappings;

    private ScopeMappingReconciler(final ResourceType type, final ScopeMappingGateway scopeMappings) {
        this.type = type;
        this.scopeMappings = Objects.requireNonNull(scopeMappings, "scopeMappings");
    }

    public static ScopeMappingReconciler ofRealmRoles(final ScopeMappingGateway scopeMappings) {
        return new ScopeMappingReconciler(ResourceType.SCOPE_MAPPING, scopeMappings);
    }

    public static ScopeMappingReconciler ofClientRoles(final ScopeMappingGateway scopeMappings) {
        return new ScopeMappingReconciler(ResourceType.CLIENT_SCOPE_MAPPING, scopeMappings);
    }

    @Override
    public ResourceType type() {
        return this.type;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(
                ResourceType.REALM_ROLE, ResourceType.CLIENT_ROLE, ResourceType.CLIENT, ResourceType.CLIENT_SCOPE);
    }

    @Override
    public List<ScopeMapping> read(final RealmContext context, final Collection<ScopeMapping> desired) {
        return this.scopeMappings.findAll(context.realm(), this.type == ResourceType.CLIENT_SCOPE_MAPPING);
    }

    @Override
    public ScopeMapping merge(final RealmContext context, final ScopeMapping current, final ScopeMapping desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final ScopeMapping resource) {
        if (!resource.roles().isEmpty()) {
            this.scopeMappings.addRoles(context.realm(), resource.key(), resource.roles());
        }
    }

    @Override
    public void update(final RealmContext context, final ScopeMapping current, final ScopeMapping target) {
        Set<String> added = new LinkedHashSet<>(target.roles());
        added.removeAll(current.roles());
        if (!added.isEmpty()) {
            this.scopeMappings.addRoles(context.realm(), target.key(), added);
        }
        Set<String> removed = new LinkedHashSet<>(current.roles());
        removed.removeAll(target.roles());
        if (!removed.isEmpty()) {
            this.scopeMappings.removeRoles(context.realm(), target.key(), removed);
        }
    }

    /** A scope mapping is deleted by removing all its roles. */
    @Override
    public void delete(final RealmContext context, final ScopeMapping resource) {
        this.scopeMappings.removeRoles(context.realm(), resource.key(), resource.roles());
    }

    @Override
    public String stateScope(final ScopeMapping resource) {
        return this.type == ResourceType.SCOPE_MAPPING ? "scope-mappings" : "client-scope-mappings";
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final ScopeMapping resource) {
        return resource.isOwnedByKeycloak();
    }

    @Override
    public boolean usesRemoteState() {
        return false;
    }
}
