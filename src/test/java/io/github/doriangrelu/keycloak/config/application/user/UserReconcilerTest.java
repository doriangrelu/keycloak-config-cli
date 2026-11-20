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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.user.port.UserGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserReconcilerTest {

    /** Keycloak gives the default roles of the realm to every new user. */
    static final class InMemoryUsers implements UserGateway {
        final Map<String, User> users = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        @Override
        public Optional<User> find(String realm, User desired) {
            User found = this.users.get(desired.username());
            if (found == null && desired.email() != null) {
                found = this.users.values().stream()
                        .filter(user -> desired.email().equals(user.email()))
                        .findFirst()
                        .orElse(null);
            }
            return Optional.ofNullable(found);
        }

        @Override
        public void create(String realm, User user) {
            this.log.add("create " + user.username());
            this.users.put(
                    user.username(),
                    new User(
                            user.username(),
                            null,
                            user.email(),
                            null,
                            null,
                            null,
                            null,
                            Map.of(),
                            null,
                            Set.of(User.defaultRolesOf(realm)),
                            Map.of(),
                            Set.of(),
                            null));
        }

        @Override
        public void update(String realm, User current, User target) {
            this.log.add("update " + target.username());
            this.users.remove(current.previousUsername() != null ? current.previousUsername() : current.username());
            this.users.put(target.username(), target.withoutCredentials());
        }

        @Override
        public Set<String> keycloakClients(String realm) {
            return Set.of("account");
        }
    }

    private InMemoryUsers keycloak;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryUsers();
    }

    private ReconciliationEngine engine(boolean removeDefaultRole) {
        return new ReconciliationEngine(
                List.of(new UserReconciler(this.keycloak, new UserImportRules(removeDefaultRole, false))));
    }

    private static RealmContext context() {
        return new RealmContext("test", true);
    }

    private static User declared(String username, String email, Set<String> realmRoles) {
        return new User(username, null, email, null, null, null, null, null, null, realmRoles, null, null, null);
    }

    private void apply(ReconciliationEngine engine, User... users) {
        engine.apply(context(), new DesiredState().declare(ResourceType.USER, List.of(users)));
    }

    @Test
    void aCreatedUserKeepsTheDefaultRolesUnlessAsked() {
        apply(engine(false), declared("u", null, Set.of("reader")));

        assertThat(this.keycloak.users.get("u").realmRoles()).containsExactlyInAnyOrder("reader", "default-roles-test");

        apply(engine(true), declared("u", null, Set.of("reader")));

        assertThat(this.keycloak.users.get("u").realmRoles()).containsExactly("reader");
    }

    @Test
    void anUnchangedUserIsNotUpdated() {
        ReconciliationEngine engine = engine(false);
        apply(engine, declared("u", null, Set.of("reader")));
        this.keycloak.log.clear();

        apply(engine, declared("u", null, Set.of("reader")));

        assertThat(this.keycloak.log).isEmpty();
    }

    @Test
    void aUserFoundByItsEmailIsRenamed() {
        apply(engine(false), declared("jake", "jake@mail", Set.of()));

        apply(engine(false), declared("john", "jake@mail", Set.of()));

        assertThat(this.keycloak.users).containsOnlyKeys("john");
        assertThat(this.keycloak.log).containsExactly("create jake", "update jake", "update john");
    }

    @Test
    void usersAreNeverDeleted() {
        ReconciliationEngine engine = engine(false);
        RealmContext context = context();
        engine.apply(context, new DesiredState().declare(ResourceType.USER, List.of(declared("u", null, null))));
        engine.apply(context, new DesiredState().declare(ResourceType.USER, List.of(declared("v", null, null))));

        engine.prune(context);

        assertThat(this.keycloak.users).containsOnlyKeys("u", "v");
    }
}
