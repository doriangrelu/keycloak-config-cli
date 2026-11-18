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

package io.github.doriangrelu.keycloak.config.application.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.role.port.RoleGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleReconcilersTest {

    /** Roles of one realm, in memory. */
    static final class InMemoryRoles implements RoleGateway {
        final Map<RoleKey, Role> roles = new LinkedHashMap<>();
        final Set<String> clients;

        InMemoryRoles(Set<String> clients) {
            this.clients = clients;
        }

        void add(Role role) {
            this.roles.put(role.key(), role);
        }

        @Override
        public List<Role> findRealmRoles(String realm) {
            return this.roles.values().stream()
                    .filter(role -> !role.key().isClientRole())
                    .toList();
        }

        @Override
        public Optional<List<Role>> findClientRoles(String realm, String clientId) {
            if (!this.clients.contains(clientId)) {
                return Optional.empty();
            }
            return Optional.of(this.roles.values().stream()
                    .filter(role -> clientId.equals(role.key().clientId()))
                    .toList());
        }

        @Override
        public void create(String realm, Role role) {
            role.key()
                    .client()
                    .filter(clientId -> !this.clients.contains(clientId))
                    .ifPresent(clientId -> {
                        throw new UnknownClientException(realm, clientId, role.name());
                    });
            this.roles.put(role.key(), role);
        }

        @Override
        public void update(String realm, Role role) {
            this.roles.put(role.key(), role);
        }

        @Override
        public void delete(String realm, RoleKey key) {
            this.roles.remove(key);
        }
    }

    private InMemoryRoles keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryRoles(Set.of("app", "other-app", "realm-management"));
        this.engine = new ReconciliationEngine(
                List.of(new ClientRoleReconciler(this.keycloak), new RealmRoleReconciler(this.keycloak)));
    }

    private static Role realmRole(String name, String description) {
        return new Role(RoleKey.realm(name), description, null, null);
    }

    private static Role clientRole(String clientId, String name) {
        return new Role(RoleKey.client(clientId, name), null, null, null);
    }

    private static RealmContext context() {
        return new RealmContext("test", false, false, Map.of());
    }

    @Test
    void realmRolesAreCreatedUpdatedAndPruned() {
        this.keycloak.add(realmRole("kept", "old"));
        this.keycloak.add(realmRole("orphan", null));
        RealmContext context = context();

        this.engine.apply(
                context,
                new DesiredState()
                        .declare(ResourceType.REALM_ROLE, List.of(realmRole("kept", "new"), realmRole("added", null))));
        this.engine.prune(context);

        assertThat(this.keycloak.roles.values())
                .containsExactlyInAnyOrder(realmRole("kept", "new"), realmRole("added", null));
    }

    @Test
    void defaultRolesAreNeverPruned() {
        this.keycloak.add(realmRole("offline_access", "${role_offline-access}"));
        this.keycloak.add(realmRole("default-roles-test", "${role_default-roles}"));
        RealmContext context = context();

        this.engine.apply(context, new DesiredState().declare(ResourceType.REALM_ROLE, List.of()));
        this.engine.prune(context);

        assertThat(this.keycloak.roles).hasSize(2);
    }

    @Test
    void onlyClientsWithDeclaredRolesAreCleaned() {
        this.keycloak.add(clientRole("app", "orphan"));
        this.keycloak.add(clientRole("other-app", "untouched"));
        this.keycloak.add(clientRole("realm-management", "view-users"));
        RealmContext context = context();

        this.engine.apply(
                context, new DesiredState().declare(ResourceType.CLIENT_ROLE, List.of(clientRole("app", "reader"))));
        this.engine.prune(context);

        assertThat(this.keycloak.roles.keySet())
                .containsExactlyInAnyOrder(
                        RoleKey.client("app", "reader"),
                        RoleKey.client("other-app", "untouched"),
                        RoleKey.client("realm-management", "view-users"));
    }

    @Test
    void aClientDeclaredWithoutRolesLosesItsRoles() {
        this.keycloak.add(clientRole("app", "orphan"));
        this.keycloak.add(clientRole("other-app", "untouched"));
        RealmContext context = context();

        this.engine.apply(context, new DesiredState().declare(ResourceType.CLIENT_ROLE, List.of(), Set.of("app")));
        this.engine.prune(context);

        assertThat(this.keycloak.roles.keySet()).containsExactly(RoleKey.client("other-app", "untouched"));
    }

    @Test
    void aRoleForAMissingClientIsReported() {
        DesiredState part = new DesiredState()
                .declare(ResourceType.CLIENT_ROLE, List.of(clientRole("non-exists-client", "my_second_client_role")));

        assertThatThrownBy(() -> this.engine.apply(context(), part))
                .isInstanceOf(UnknownClientException.class)
                .hasMessage("Can't create role 'my_second_client_role' for non existing client 'non-exists-client' in"
                        + " realm 'test'!");
    }

    @Test
    void withRemoteStateOnlyRolesCreatedByTheToolArePruned() {
        this.keycloak.add(realmRole("created-by-tool", null));
        this.keycloak.add(realmRole("created-by-hand", null));
        this.keycloak.add(clientRole("app", "created-by-tool"));
        this.keycloak.add(clientRole("app", "created-by-hand"));
        RealmContext context = new RealmContext("test", false, true, Map.of());
        context.captureState(RealmRoleReconciler.STATE_SCOPE, List.of("created-by-tool"));
        context.captureState(ClientRoleReconciler.stateScope("app"), List.of("created-by-tool"));

        this.engine.apply(
                context,
                new DesiredState()
                        .declare(ResourceType.REALM_ROLE, List.of())
                        .declare(ResourceType.CLIENT_ROLE, new ArrayList<>()));
        this.engine.prune(context);

        assertThat(this.keycloak.roles.keySet())
                .containsExactlyInAnyOrder(
                        RoleKey.realm("created-by-hand"),
                        RoleKey.client("app", "created-by-tool"),
                        RoleKey.client("app", "created-by-hand"));
    }
}
