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
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Settings of the realm: the realm is created when missing, and its declared settings applied otherwise. A realm is
 * never deleted.
 */
public final class RealmSettingsReconciler implements Reconciler<String, RealmSettings> {

    private final RealmGateway realms;
    private final String ownAttributePrefix;

    /** @param ownAttributePrefix prefix of the realm attributes where keycloak-config-cli keeps its state */
    public RealmSettingsReconciler(final RealmGateway realms, final String ownAttributePrefix) {
        this.realms = Objects.requireNonNull(realms, "realms");
        this.ownAttributePrefix = Objects.requireNonNull(ownAttributePrefix, "ownAttributePrefix");
    }

    @Override
    public ResourceType type() {
        return ResourceType.REALM_SETTINGS;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<RealmSettings> read(final RealmContext context, final Collection<RealmSettings> desired) {
        return this.realms.findSettings(context.realm()).map(List::of).orElse(List.of());
    }

    @Override
    public RealmSettings merge(final RealmContext context, final RealmSettings current, final RealmSettings desired) {
        return current.mergedWith(desired, this.ownAttributePrefix);
    }

    @Override
    public void create(final RealmContext context, final RealmSettings resource) {
        this.realms.create(resource);
    }

    @Override
    public void update(final RealmContext context, final RealmSettings current, final RealmSettings target) {
        this.realms.update(target);
    }

    @Override
    public void delete(final RealmContext context, final RealmSettings resource) {
        throw new UnsupportedOperationException("A realm is never deleted");
    }

    @Override
    public String stateScope(final RealmSettings resource) {
        return "realm";
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final RealmSettings resource) {
        return true;
    }

    @Override
    public boolean prunes() {
        return false;
    }

    /** Each import file of a realm declares some of its settings: the last file wins, as before. */
    @Override
    public boolean tracksDeclarations() {
        return false;
    }
}
