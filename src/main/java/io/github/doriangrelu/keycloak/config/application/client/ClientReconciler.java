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

package io.github.doriangrelu.keycloak.config.application.client;

import io.github.doriangrelu.keycloak.config.application.client.port.ClientGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Clients, managed by {@code import.managed.client}. Keycloak's own clients are never created nor deleted. Their
 * authorization settings and authentication flow binding overrides are still imported by the legacy services.
 */
public final class ClientReconciler implements Reconciler<String, Client> {

    /** Remote state entry of the clients created by keycloak-config-cli. */
    public static final String STATE_SCOPE = "clients";

    private final ClientGateway clients;

    public ClientReconciler(final ClientGateway clients) {
        this.clients = Objects.requireNonNull(clients, "clients");
    }

    @Override
    public ResourceType type() {
        return ResourceType.CLIENT;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.CLIENT_SCOPE);
    }

    @Override
    public List<Client> read(final RealmContext context, final Collection<Client> desired) {
        Set<String> withSecret = desired.stream()
                .filter(client -> client.secret() != null && client.clientId() != null)
                .map(Client::clientId)
                .collect(Collectors.toSet());
        return this.clients.findAll(context.realm(), withSecret);
    }

    @Override
    public Client merge(final RealmContext context, final Client current, final Client desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final Client resource) {
        if (resource.isSystemClient()) {
            throw ClientRejectedException.systemClient(resource.identifier(), context.realm());
        }
        this.clients.create(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final Client current, final Client target) {
        this.clients.update(context.realm(), current, target);
    }

    @Override
    public void delete(final RealmContext context, final Client resource) {
        this.clients.delete(context.realm(), resource.clientId());
    }

    @Override
    public String stateScope(final Client resource) {
        return STATE_SCOPE;
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final Client resource) {
        return resource.isOwnedByKeycloak(context.realm());
    }
}
