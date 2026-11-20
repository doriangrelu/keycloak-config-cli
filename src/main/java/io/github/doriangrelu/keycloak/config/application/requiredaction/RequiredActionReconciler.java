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

package io.github.doriangrelu.keycloak.config.application.requiredaction;

import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.application.requiredaction.port.RequiredActionGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Required actions, managed by {@code import.managed.required-action}. */
public final class RequiredActionReconciler implements Reconciler<String, RequiredAction> {

    /** Remote state entry of the required actions created by keycloak-config-cli. */
    public static final String STATE_SCOPE = "required-actions";

    private final RequiredActionGateway requiredActions;

    public RequiredActionReconciler(final RequiredActionGateway requiredActions) {
        this.requiredActions = Objects.requireNonNull(requiredActions, "requiredActions");
    }

    @Override
    public ResourceType type() {
        return ResourceType.REQUIRED_ACTION;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<RequiredAction> read(final RealmContext context, final Collection<RequiredAction> desired) {
        return this.requiredActions.findAll(context.realm());
    }

    @Override
    public RequiredAction merge(
            final RealmContext context, final RequiredAction current, final RequiredAction desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final RequiredAction resource) {
        this.requiredActions.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final RequiredAction current, final RequiredAction target) {
        if (current.needsRecreationFor(target)) {
            this.requiredActions.delete(context.realm(), current.alias());
            this.requiredActions.create(context.realm(), target);
            return;
        }
        this.requiredActions.update(context.realm(), current.alias(), target);
    }

    @Override
    public void delete(final RealmContext context, final RequiredAction resource) {
        this.requiredActions.delete(context.realm(), resource.alias());
    }

    @Override
    public String stateScope(final RequiredAction resource) {
        return STATE_SCOPE;
    }

    /** Built-in required actions can be deleted, as the legacy import did; the remote state usually prevents it. */
    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final RequiredAction resource) {
        return resource.isOwnedByKeycloak();
    }
}
