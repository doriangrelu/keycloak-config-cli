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

package io.github.doriangrelu.keycloak.config.application.user;

import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.application.user.port.UserGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.domain.user.model.UserMergeRules;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Users, their role mappings and their groups. As in the legacy import:
 *
 * <ul>
 *   <li>users are never deleted;
 *   <li>an existing user is found by its username, else by its email, first name and last name, and then renamed;
 *   <li>the role mappings and groups of a user are the declared ones, except the default roles of the realm and the
 *       roles of the clients created by Keycloak, kept unless declared;
 *   <li>credentials labelled {@code initial} are only set when the user is created.
 * </ul>
 */
public final class UserReconciler implements Reconciler<String, User> {

    private final UserGateway users;
    private final UserImportRules rules;

    public UserReconciler(final UserGateway users, final UserImportRules rules) {
        this.users = Objects.requireNonNull(users, "users");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    @Override
    public ResourceType type() {
        return ResourceType.USER;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.REALM_ROLE, ResourceType.CLIENT_ROLE, ResourceType.CLIENT, ResourceType.GROUP);
    }

    /** Only the declared users are read: a realm may hold far more users than an import declares. */
    @Override
    public List<User> read(final RealmContext context, final Collection<User> desired) {
        return desired.stream()
                .map(user -> this.users.find(context.realm(), user).map(found -> found.matchedTo(user.username())))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public User merge(final RealmContext context, final User current, final User desired) {
        return current.mergedWith(desired, mergeRules(context.realm(), current, desired));
    }

    /**
     * Creates the user, then gives it its role mappings and groups: Keycloak has already given it the default roles of
     * the realm.
     */
    @Override
    public void create(final RealmContext context, final User user) {
        this.users.create(context.realm(), user);
        User created = this.users
                .find(context.realm(), user)
                .map(found -> found.matchedTo(user.username()))
                .orElseThrow(() -> new IllegalStateException(
                        "User '" + user.username() + "' not found after its creation in realm " + context.realm()));
        User withoutCredentials = user.withoutCredentials();
        this.users.update(
                context.realm(),
                created,
                created.mergedWith(withoutCredentials, mergeRules(context.realm(), created, withoutCredentials)));
    }

    @Override
    public void update(final RealmContext context, final User current, final User target) {
        this.users.update(context.realm(), current, target);
    }

    @Override
    public void delete(final RealmContext context, final User resource) {
        throw new UnsupportedOperationException("Users are never deleted by an import");
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final User resource) {
        return false;
    }

    @Override
    public boolean prunes() {
        return false;
    }

    private UserMergeRules mergeRules(final String realm, final User current, final User desired) {
        Set<String> keptRealmRoles = this.rules.removeDefaultRole() ? Set.of() : Set.of(User.defaultRolesOf(realm));
        Map<String, Set<String>> declared = desired.clientRoles() == null ? Map.of() : desired.clientRoles();
        boolean undeclaredClients = current.clientRoles() != null
                && current.clientRoles().keySet().stream().anyMatch(clientId -> !declared.containsKey(clientId));
        Set<String> keycloakClients = undeclaredClients ? this.users.keycloakClients(realm) : Set.of();
        boolean keepAttributes = this.rules.skipAttributesForFederatedUser() && current.federationLink() != null;
        return new UserMergeRules(keptRealmRoles, keycloakClients, keepAttributes);
    }
}
