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

package io.github.doriangrelu.keycloak.config.application.userprofile;

import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.application.userprofile.port.UserProfileGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.userprofile.model.UserProfile;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * User profile configuration of the realm: the declared configuration replaces the current one. A realm always has
 * one, so it is never deleted.
 */
public final class UserProfileReconciler implements Reconciler<String, UserProfile> {

    private final UserProfileGateway userProfiles;

    public UserProfileReconciler(final UserProfileGateway userProfiles) {
        this.userProfiles = Objects.requireNonNull(userProfiles, "userProfiles");
    }

    @Override
    public ResourceType type() {
        return ResourceType.USER_PROFILE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<UserProfile> read(final RealmContext context, final Collection<UserProfile> desired) {
        return List.of(this.userProfiles.find(context.realm()));
    }

    @Override
    public UserProfile merge(final RealmContext context, final UserProfile current, final UserProfile desired) {
        return desired;
    }

    @Override
    public void create(final RealmContext context, final UserProfile resource) {
        this.userProfiles.save(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final UserProfile current, final UserProfile target) {
        this.userProfiles.save(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final UserProfile resource) {
        throw new UnsupportedOperationException("The user profile of a realm is never deleted");
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final UserProfile resource) {
        return true;
    }

    @Override
    public boolean prunes() {
        return false;
    }
}
