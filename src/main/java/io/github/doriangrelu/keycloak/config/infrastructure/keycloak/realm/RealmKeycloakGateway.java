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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm;

import io.github.doriangrelu.keycloak.config.application.realm.RealmRejectedException;
import io.github.doriangrelu.keycloak.config.application.realm.UnknownRealmReferenceException;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultClientScopes;
import io.github.doriangrelu.keycloak.config.domain.realm.model.DefaultGroups;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientScopeApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RealmApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link RealmGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RealmKeycloakGateway implements RealmGateway {

    /** Longest value some Keycloak versions store for these properties without a database error. */
    private static final int MAX_LENGTH = 241;

    /** Flow bindings of a realm, by name in the import. */
    private record FlowBinding(
            String name,
            Function<RealmRepresentation, String> getter,
            BiConsumer<RealmRepresentation, String> setter) {}

    private static final List<FlowBinding> FLOW_BINDINGS = List.of(
            new FlowBinding("browserFlow", RealmRepresentation::getBrowserFlow, RealmRepresentation::setBrowserFlow),
            new FlowBinding(
                    "directGrantFlow",
                    RealmRepresentation::getDirectGrantFlow,
                    RealmRepresentation::setDirectGrantFlow),
            new FlowBinding(
                    "clientAuthenticationFlow",
                    RealmRepresentation::getClientAuthenticationFlow,
                    RealmRepresentation::setClientAuthenticationFlow),
            new FlowBinding(
                    "dockerAuthenticationFlow",
                    RealmRepresentation::getDockerAuthenticationFlow,
                    RealmRepresentation::setDockerAuthenticationFlow),
            new FlowBinding(
                    "registrationFlow",
                    RealmRepresentation::getRegistrationFlow,
                    RealmRepresentation::setRegistrationFlow),
            new FlowBinding(
                    "resetCredentialsFlow",
                    RealmRepresentation::getResetCredentialsFlow,
                    RealmRepresentation::setResetCredentialsFlow));

    private final RealmApi realmApi;
    private final ClientScopeApi clientScopeApi;
    private final GroupApi groupApi;
    private final RealmKeycloakMapper mapper;

    public RealmKeycloakGateway(
            final RealmApi realmApi,
            final ClientScopeApi clientScopeApi,
            final GroupApi groupApi,
            final RealmKeycloakMapper mapper) {
        this.realmApi = realmApi;
        this.clientScopeApi = clientScopeApi;
        this.groupApi = groupApi;
        this.mapper = mapper;
    }

    @Override
    public Optional<RealmSettings> findSettings(final String realm) {
        return this.realmApi.findRealm(realm).map(this.mapper::toCurrentSettings);
    }

    @Override
    public void create(final RealmSettings settings) {
        RealmRepresentation representation = this.mapper.toRepresentation(settings);
        requireStorable(representation, "create");
        try {
            this.realmApi.createRealm(representation);
        } catch (final KeycloakAccessException error) {
            throw new RealmRejectedException(
                    String.format("Cannot create realm '%s': %s", settings.realm(), error.getMessage()), error);
        }
    }

    @Override
    public void update(final RealmSettings settings) {
        RealmRepresentation representation = this.mapper.toRepresentation(settings);
        requireStorable(representation, "update");
        try {
            this.realmApi.updateRealm(settings.realm(), representation);
        } catch (final KeycloakAccessException error) {
            throw new RealmRejectedException(
                    String.format("Cannot update realm '%s': %s", settings.realm(), error.getMessage()), error);
        }
    }

    /** Some Keycloak versions fail at the database level on these values: refused beforehand with a clear message. */
    private static void requireStorable(final RealmRepresentation realm, final String action) {
        if (realm.getRealm() != null && realm.getRealm().length() > MAX_LENGTH) {
            throw new RealmRejectedException(
                    String.format(
                            "Cannot %s realm '%s': Property value too long for database column",
                            action, realm.getRealm()),
                    null);
        }
        if (realm.getLoginTheme() != null && realm.getLoginTheme().length() > MAX_LENGTH) {
            throw new RealmRejectedException(
                    String.format(
                            "Cannot %s realm '%s': Property 'loginTheme' value too long for database column",
                            action, realm.getRealm()),
                    null);
        }
    }

    @Override
    public DefaultClientScopes findDefaultClientScopes(final String realm, final DefaultClientScopes.Kind kind) {
        return new DefaultClientScopes(
                kind,
                this.realmApi.listDefaultClientScopes(realm, isOptional(kind)).stream()
                        .map(ClientScopeRepresentation::getName)
                        .collect(Collectors.toSet()));
    }

    @Override
    public void addDefaultClientScopes(
            final String realm, final DefaultClientScopes.Kind kind, final Set<String> scopes) {
        Map<String, String> idsByName = clientScopeIdsByName(realm);
        for (String scope : scopes) {
            String id = idsByName.get(scope);
            if (id == null) {
                throw UnknownRealmReferenceException.clientScope(realm, scope);
            }
            this.realmApi.addDefaultClientScope(realm, isOptional(kind), id);
        }
    }

    @Override
    public void removeDefaultClientScopes(
            final String realm, final DefaultClientScopes.Kind kind, final Set<String> scopes) {
        Map<String, String> idsByName = clientScopeIdsByName(realm);
        scopes.stream()
                .map(idsByName::get)
                .filter(Objects::nonNull)
                .forEach(id -> this.realmApi.removeDefaultClientScope(realm, isOptional(kind), id));
    }

    private Map<String, String> clientScopeIdsByName(final String realm) {
        return this.clientScopeApi.listClientScopes(realm).stream()
                .collect(Collectors.toMap(
                        ClientScopeRepresentation::getName,
                        ClientScopeRepresentation::getId,
                        (first, second) -> first));
    }

    private static boolean isOptional(final DefaultClientScopes.Kind kind) {
        return kind == DefaultClientScopes.Kind.OPTIONAL;
    }

    @Override
    public DefaultGroups findDefaultGroups(final String realm) {
        return new DefaultGroups(this.realmApi
                .findRealm(realm)
                .map(RealmRepresentation::getDefaultGroups)
                .map(Set::copyOf)
                .orElse(Set.of()));
    }

    @Override
    public void addDefaultGroups(final String realm, final Set<String> paths) {
        for (String path : paths) {
            String id = this.groupApi
                    .findByPath(realm, path)
                    .map(GroupRepresentation::getId)
                    .orElseThrow(() -> UnknownRealmReferenceException.group(path));
            this.realmApi.addDefaultGroup(realm, id);
        }
    }

    /** Flow bindings an import declares. */
    public static RealmFlowBindings flowBindingsOf(final RealmRepresentation realm) {
        Map<String, String> flows = new LinkedHashMap<>();
        FLOW_BINDINGS.forEach(
                binding -> flows.put(binding.name(), binding.getter().apply(realm)));
        return new RealmFlowBindings(flows);
    }

    @Override
    public RealmFlowBindings findFlowBindings(final String realm) {
        return this.realmApi
                .findRealm(realm)
                .map(RealmKeycloakGateway::flowBindingsOf)
                .orElse(new RealmFlowBindings(Map.of()));
    }

    @Override
    public void bindFlows(final String realm, final RealmFlowBindings bindings) {
        RealmRepresentation existing = this.realmApi
                .findRealm(realm)
                .orElseThrow(() -> new IllegalStateException("Realm '" + realm + "' is missing"));
        FLOW_BINDINGS.forEach(binding -> {
            String alias = bindings.flows().get(binding.name());
            if (alias != null) {
                binding.setter().accept(existing, alias);
            }
        });
        try {
            this.realmApi.updateRealm(realm, existing);
        } catch (final KeycloakAccessException error) {
            throw new RealmRejectedException(
                    String.format("Cannot update realm '%s': %s", realm, error.getMessage()), error);
        }
    }

    @Override
    public void removeDefaultGroups(final String realm, final Set<String> paths) {
        paths.forEach(path -> this.groupApi
                .findByPath(realm, path)
                .ifPresent(group -> this.realmApi.removeDefaultGroup(realm, group.getId())));
    }
}
