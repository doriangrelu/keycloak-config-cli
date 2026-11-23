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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.user;

import io.github.doriangrelu.keycloak.config.application.user.UnknownUserReferenceException;
import io.github.doriangrelu.keycloak.config.application.user.port.UserGateway;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserApi;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link UserGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class UserKeycloakGateway implements UserGateway {

    private static final Logger logger = LoggerFactory.getLogger(UserKeycloakGateway.class);

    private final UserApi userApi;
    private final RoleApi roleApi;
    private final GroupApi groupApi;
    private final ClientApi clientApi;
    private final UserKeycloakMapper mapper;
    private final KeycloakImportOptions options;

    public UserKeycloakGateway(
            final UserApi userApi,
            final RoleApi roleApi,
            final GroupApi groupApi,
            final ClientApi clientApi,
            final UserKeycloakMapper mapper,
            final KeycloakImportOptions options) {
        this.userApi = userApi;
        this.roleApi = roleApi;
        this.groupApi = groupApi;
        this.clientApi = clientApi;
        this.mapper = mapper;
        this.options = options;
    }

    @Override
    public Optional<User> find(final String realm, final User desired) {
        return findRepresentation(realm, desired.username(), desired).map(user -> toUser(realm, user));
    }

    @Override
    public void create(final String realm, final User user) {
        logger.debug("Create user '{}' in realm '{}'", user.username(), realm);
        withoutRefusedPassword(realm, user, candidate -> this.userApi.createUser(realm, toRepresentation(candidate)));
    }

    @Override
    public void update(final String realm, final User current, final User target) {
        String previous = current.previousUsername() != null ? current.previousUsername() : current.username();
        String userId = findRepresentation(realm, previous, target)
                .map(UserRepresentation::getId)
                .orElseThrow(() -> new IllegalStateException("User '" + previous + "' not found in realm " + realm));
        if (!Objects.equals(current.withoutMappings(), target.withoutMappings())) {
            logger.debug("Update user '{}' in realm '{}'", target.username(), realm);
            withoutRefusedPassword(realm, target, candidate -> {
                UserRepresentation representation = toRepresentation(candidate);
                representation.setId(userId);
                this.userApi.updateUser(realm, userId, representation);
            });
        }
        updateRealmRoles(realm, userId, current.realmRoles(), target.realmRoles());
        updateClientRoles(realm, userId, current.clientRoles(), target.clientRoles());
        updateGroups(realm, userId, current.groups(), target.groups());
    }

    @Override
    public Set<String> keycloakClients(final String realm) {
        return this.clientApi.listClients(realm).stream()
                .filter(UserKeycloakGateway::isKeycloakClient)
                .map(ClientRepresentation::getClientId)
                .collect(Collectors.toSet());
    }

    /** A client created by Keycloak is named after its client id, as a message key: {@code ${client_<clientId>}}. */
    private static boolean isKeycloakClient(final ClientRepresentation client) {
        return client.getClientId() != null
                && client.getName() != null
                && (client.getName().equals("${client_" + client.getClientId() + "}")
                        || client.getName()
                                .equals("${client_" + client.getClientId().replace("_", "-") + "}"));
    }

    private Optional<UserRepresentation> findRepresentation(
            final String realm, final String username, final User identity) {
        Optional<UserRepresentation> found = this.userApi.findByUsername(realm, username);
        if (found.isPresent() || identity.email() == null) {
            return found;
        }
        return this.userApi.search(realm, identity.email(), identity.firstName(), identity.lastName()).stream()
                .filter(user -> equalsIgnoreCase(identity.email(), user.getEmail())
                        && equalsIgnoreCase(identity.firstName(), user.getFirstName())
                        && equalsIgnoreCase(identity.lastName(), user.getLastName()))
                .findFirst()
                .map(user -> this.userApi.getUser(realm, user.getId()));
    }

    private User toUser(final String realm, final UserRepresentation user) {
        Set<String> realmRoles = this.userApi.listRealmRoles(realm, user.getId()).stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Set<String>> clientRoles = new LinkedHashMap<>();
        this.userApi
                .listClientRoles(realm, user.getId())
                .forEach((clientId, roles) -> clientRoles.put(
                        clientId,
                        roles.stream()
                                .map(RoleRepresentation::getName)
                                .collect(Collectors.toCollection(LinkedHashSet::new))));
        Set<String> groups = this.userApi.listGroups(realm, user.getId()).stream()
                .map(GroupRepresentation::getPath)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return this.mapper.toUser(user, realmRoles, clientRoles, groups);
    }

    private UserRepresentation toRepresentation(final User user) {
        UserRepresentation representation = this.mapper.toRepresentation(user);
        if (user.federationLink() != null && this.options.skipAttributesForFederatedUser()) {
            representation.setAttributes(null);
        }
        return representation;
    }

    /**
     * Writes the user; when the password policy of the realm refuses its password (password history), writes it again
     * without password, as the legacy import did.
     */
    private void withoutRefusedPassword(final String realm, final User user, final Consumer<User> write) {
        try {
            write.accept(user);
        } catch (final KeycloakAccessException error) {
            if (!user.hasPassword() || !isPasswordPolicyViolation(error)) {
                throw error;
            }
            logger.warn(
                    "Password policy violation detected for user '{}' in realm '{}'. Retrying without password: "
                            + "the password is NOT set, all other attributes are applied.",
                    user.username(),
                    realm);
            write.accept(user.withoutPassword());
        }
    }

    private static boolean isPasswordPolicyViolation(final KeycloakAccessException error) {
        if (error.getFailure() != KeycloakFailure.REJECTED || error.getMessage() == null) {
            return false;
        }
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("invalidpasswordhistorymessage")
                || message.contains("password history")
                || message.contains("passwordpolicynotmetexception");
    }

    private void updateRealmRoles(
            final String realm, final String userId, final Set<String> current, final Set<String> target) {
        Set<String> currentRoles = orEmpty(current);
        Set<String> targetRoles = orEmpty(target);
        List<RoleRepresentation> added = minus(targetRoles, currentRoles).stream()
                .map(name -> this.roleApi
                        .findRealmRole(realm, name)
                        .orElseThrow(() -> UnknownUserReferenceException.realmRole(realm, name)))
                .toList();
        if (!added.isEmpty()) {
            this.userApi.addRealmRoles(realm, userId, added);
        }
        List<RoleRepresentation> removed = minus(currentRoles, targetRoles).stream()
                .map(name -> this.roleApi.findRealmRole(realm, name))
                .flatMap(Optional::stream)
                .toList();
        if (!removed.isEmpty()) {
            this.userApi.removeRealmRoles(realm, userId, removed);
        }
    }

    private void updateClientRoles(
            final String realm,
            final String userId,
            final Map<String, Set<String>> current,
            final Map<String, Set<String>> target) {
        Map<String, Set<String>> currentRoles = current == null ? Map.of() : current;
        Map<String, Set<String>> targetRoles = target == null ? Map.of() : target;
        Set<String> clients = new LinkedHashSet<>(currentRoles.keySet());
        clients.addAll(targetRoles.keySet());
        for (String clientId : clients) {
            Set<String> currentOfClient = currentRoles.getOrDefault(clientId, Set.of());
            Set<String> targetOfClient = targetRoles.getOrDefault(clientId, Set.of());
            if (currentOfClient.equals(targetOfClient)) {
                continue;
            }
            String clientUuid = this.roleApi
                    .findClientUuid(realm, clientId)
                    .orElseThrow(() -> UnknownUserReferenceException.client(clientId));
            List<RoleRepresentation> added = minus(targetOfClient, currentOfClient).stream()
                    .map(name -> this.roleApi
                            .findClientRole(realm, clientUuid, name)
                            .orElseThrow(() -> UnknownUserReferenceException.clientRole(realm, clientId, name)))
                    .toList();
            if (!added.isEmpty()) {
                this.userApi.addClientRoles(realm, userId, clientUuid, added);
            }
            List<RoleRepresentation> removed = minus(currentOfClient, targetOfClient).stream()
                    .map(name -> this.roleApi.findClientRole(realm, clientUuid, name))
                    .flatMap(Optional::stream)
                    .toList();
            if (!removed.isEmpty()) {
                this.userApi.removeClientRoles(realm, userId, clientUuid, removed);
            }
        }
    }

    private void updateGroups(
            final String realm, final String userId, final Set<String> current, final Set<String> target) {
        Set<String> currentGroups = orEmpty(current);
        Set<String> targetGroups = orEmpty(target);
        for (String path : minus(targetGroups, currentGroups)) {
            GroupRepresentation group = this.groupApi
                    .findByPath(realm, path)
                    .orElseThrow(() -> UnknownUserReferenceException.group(realm, path));
            this.userApi.joinGroup(realm, userId, group.getId());
        }
        for (String path : minus(currentGroups, targetGroups)) {
            this.groupApi
                    .findByPath(realm, path)
                    .ifPresent(group -> this.userApi.leaveGroup(realm, userId, group.getId()));
        }
    }

    private static boolean equalsIgnoreCase(final String expected, final String actual) {
        return expected == null ? actual == null : expected.equalsIgnoreCase(actual);
    }

    private static Set<String> orEmpty(final Set<String> values) {
        return values == null ? Set.of() : values;
    }

    private static Set<String> minus(final Set<String> values, final Set<String> removed) {
        Set<String> result = new LinkedHashSet<>(values);
        result.removeAll(removed);
        return result;
    }
}
