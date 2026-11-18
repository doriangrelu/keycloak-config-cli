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

package io.github.doriangrelu.keycloak.config.domain.user.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A user of a realm, identified by its username. Users are never deleted by an import.
 *
 * <p>In a desired user, a {@code null} field is not specified by the import file. The role mappings and the groups of
 * an imported user are exactly the declared ones, declared or not, except what {@link UserMergeRules} keeps.
 * Credentials labelled {@link Credential#INITIAL} are only set when the user is created.
 *
 * @param previousUsername for an existing user found by its email and names, the username it has in Keycloak;
 *     {@code null} otherwise
 * @param serviceAccountClientId client of a service account user, or {@code null}
 * @param credentials credentials to set, or {@code null} when none
 * @param groups paths of the groups of the user
 */
public record User(
        String username,
        String previousUsername,
        String email,
        String firstName,
        String lastName,
        String federationLink,
        String serviceAccountClientId,
        Map<String, List<String>> attributes,
        List<Credential> credentials,
        Set<String> realmRoles,
        Map<String, Set<String>> clientRoles,
        Set<String> groups,
        UnmodeledAttributes unmodeled)
        implements Resource<String> {

    public User {
        Objects.requireNonNull(username, "username");
        attributes = attributes == null ? null : copyOfAttributes(attributes);
        credentials = credentials == null || credentials.isEmpty() ? null : List.copyOf(credentials);
        realmRoles = realmRoles == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(realmRoles));
        clientRoles = clientRoles == null ? null : copyOfClientRoles(clientRoles);
        groups = groups == null ? null : copyOfGroups(groups);
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    /** Default roles of a realm, given to every new user by Keycloak. */
    public static String defaultRolesOf(final String realm) {
        return "default-roles-" + realm.toLowerCase(Locale.ROOT);
    }

    @Override
    public String key() {
        return this.username;
    }

    @Override
    public String stateName() {
        return this.username;
    }

    public boolean isServiceAccount() {
        return this.serviceAccountClientId != null && !this.serviceAccountClientId.isEmpty();
    }

    /**
     * This existing user, found for the desired user {@code username}: the identity of the import file. Usernames are
     * compared ignoring case, as Keycloak stores them in lower case.
     */
    public User matchedTo(final String username) {
        String previous = this.username.equalsIgnoreCase(username) ? null : this.username;
        return new User(
                username,
                previous,
                this.email,
                this.firstName,
                this.lastName,
                this.federationLink,
                this.serviceAccountClientId,
                this.attributes,
                this.credentials,
                this.realmRoles,
                this.clientRoles,
                this.groups,
                this.unmodeled);
    }

    /** This user once {@code desired} is applied on it. */
    public User mergedWith(final User desired, final UserMergeRules rules) {
        Set<String> realmRolesOf = new LinkedHashSet<>(orEmpty(desired.realmRoles));
        orEmpty(this.realmRoles).stream()
                .filter(rules.keptRealmRoles()::contains)
                .forEach(realmRolesOf::add);
        Map<String, Set<String>> clientRolesOf = new LinkedHashMap<>(orEmpty(desired.clientRoles));
        orEmpty(this.clientRoles).forEach((clientId, roles) -> {
            if (rules.keycloakClients().contains(clientId)) {
                clientRolesOf.putIfAbsent(clientId, roles);
            }
        });
        return new User(
                desired.username,
                null,
                desired.email != null ? desired.email : this.email,
                desired.firstName != null ? desired.firstName : this.firstName,
                desired.lastName != null ? desired.lastName : this.lastName,
                desired.federationLink != null ? desired.federationLink : this.federationLink,
                this.serviceAccountClientId != null ? this.serviceAccountClientId : desired.serviceAccountClientId,
                rules.keepAttributes() || desired.attributes == null ? this.attributes : desired.attributes,
                desired.credentials == null
                        ? null
                        : desired.credentials.stream()
                                .filter(credential -> !credential.isInitial())
                                .toList(),
                realmRolesOf,
                withoutEmptyClients(clientRolesOf),
                orEmpty(desired.groups),
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    /** The user itself, without its role mappings and groups: what the user is updated with. */
    public User withoutMappings() {
        return new User(
                this.username,
                this.previousUsername,
                this.email,
                this.firstName,
                this.lastName,
                this.federationLink,
                this.serviceAccountClientId,
                this.attributes,
                this.credentials,
                null,
                null,
                null,
                this.unmodeled);
    }

    public User withoutCredentials() {
        return new User(
                this.username,
                this.previousUsername,
                this.email,
                this.firstName,
                this.lastName,
                this.federationLink,
                this.serviceAccountClientId,
                this.attributes,
                null,
                this.realmRoles,
                this.clientRoles,
                this.groups,
                this.unmodeled);
    }

    /** The same user without password, when the password policy of the realm refuses it. */
    public User withoutPassword() {
        return new User(
                this.username,
                this.previousUsername,
                this.email,
                this.firstName,
                this.lastName,
                this.federationLink,
                this.serviceAccountClientId,
                this.attributes,
                this.credentials == null
                        ? null
                        : this.credentials.stream()
                                .filter(credential -> !credential.isPassword())
                                .toList(),
                this.realmRoles,
                this.clientRoles,
                this.groups,
                this.unmodeled);
    }

    public boolean hasPassword() {
        return this.credentials != null && this.credentials.stream().anyMatch(Credential::isPassword);
    }

    private static <T> Set<T> orEmpty(final Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private static <K, V> Map<K, V> orEmpty(final Map<K, V> values) {
        return values == null ? Map.of() : values;
    }

    private static Map<String, List<String>> copyOfAttributes(final Map<String, List<String>> attributes) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        attributes.forEach((key, values) -> copy.put(key, values == null ? List.of() : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    /** A client declared with no role is kept: its role mappings are removed, even for a client of Keycloak. */
    private static Map<String, Set<String>> copyOfClientRoles(final Map<String, Set<String>> clientRoles) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        clientRoles.forEach((clientId, roles) ->
                copy.put(clientId, roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles))));
        return Collections.unmodifiableMap(copy);
    }

    /** A client with no role has no mapping in Keycloak: it is dropped, so that it compares equal to Keycloak. */
    private static Map<String, Set<String>> withoutEmptyClients(final Map<String, Set<String>> clientRoles) {
        Map<String, Set<String>> mapped = new LinkedHashMap<>(clientRoles);
        mapped.values().removeIf(Set::isEmpty);
        return mapped;
    }

    /** A group may be given by its name or by its path: both are read as a path. */
    private static Set<String> copyOfGroups(final Set<String> groups) {
        Set<String> paths = new LinkedHashSet<>();
        groups.forEach(group -> paths.add(group.startsWith("/") ? group : "/" + group));
        return Collections.unmodifiableSet(paths);
    }
}
