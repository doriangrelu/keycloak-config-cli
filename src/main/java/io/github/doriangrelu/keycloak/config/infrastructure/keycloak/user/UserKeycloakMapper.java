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

import io.github.doriangrelu.keycloak.config.domain.user.model.Credential;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

/**
 * Conversions between {@link UserRepresentation} and {@link User} (ADR-006). Written by hand: credentials carry their
 * own unmodeled attributes, and role mappings and groups are read apart from the user.
 */
@Component
public class UserKeycloakMapper {

    /**
     * Fields that are not unmodeled attributes: modeled by {@link User}; computed by Keycloak ({@code id},
     * {@code access}, {@code createdTimestamp}, {@code userProfileMetadata}); deprecated ({@code applicationRoles},
     * {@code socialLinks}).
     */
    static final Set<String> NOT_UNMODELED = Set.of(
            "username",
            "email",
            "firstName",
            "lastName",
            "federationLink",
            "serviceAccountClientId",
            "attributes",
            "credentials",
            "realmRoles",
            "clientRoles",
            "groups",
            "id",
            "access",
            "createdTimestamp",
            "userProfileMetadata",
            "applicationRoles",
            "socialLinks");

    static final Set<String> CREDENTIAL_NOT_UNMODELED = Set.of("type", "userLabel");

    private final UnmodeledAttributesConverter unmodeledAttributes;

    public UserKeycloakMapper(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /** A user of an import file, with the given username. */
    public User toUser(final UserRepresentation representation, final String username) {
        return new User(
                username,
                null,
                representation.getEmail(),
                representation.getFirstName(),
                representation.getLastName(),
                representation.getFederationLink(),
                representation.getServiceAccountClientId(),
                representation.getAttributes(),
                representation.getCredentials() == null
                        ? null
                        : representation.getCredentials().stream()
                                .map(this::toCredential)
                                .toList(),
                representation.getRealmRoles() == null ? null : new LinkedHashSet<>(representation.getRealmRoles()),
                representation.getClientRoles() == null ? null : toRoleSets(representation.getClientRoles()),
                representation.getGroups() == null ? null : new LinkedHashSet<>(representation.getGroups()),
                this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    /** A user read from Keycloak, with its role mappings and groups. */
    public User toUser(
            final UserRepresentation representation,
            final Set<String> realmRoles,
            final Map<String, Set<String>> clientRoles,
            final Set<String> groups) {
        return new User(
                representation.getUsername(),
                null,
                representation.getEmail(),
                representation.getFirstName(),
                representation.getLastName(),
                representation.getFederationLink(),
                representation.getServiceAccountClientId(),
                representation.getAttributes() == null ? Map.of() : representation.getAttributes(),
                null,
                realmRoles,
                clientRoles,
                groups,
                this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    /** The user itself; role mappings and groups are written apart. */
    public UserRepresentation toRepresentation(final User user) {
        UserRepresentation representation = new UserRepresentation();
        this.unmodeledAttributes.apply(user.unmodeled(), representation);
        representation.setUsername(user.username());
        representation.setEmail(user.email());
        representation.setFirstName(user.firstName());
        representation.setLastName(user.lastName());
        representation.setFederationLink(user.federationLink());
        representation.setServiceAccountClientId(user.serviceAccountClientId());
        representation.setAttributes(user.attributes());
        if (user.credentials() != null) {
            representation.setCredentials(
                    user.credentials().stream().map(this::toRepresentation).toList());
        }
        return representation;
    }

    private Credential toCredential(final CredentialRepresentation representation) {
        return new Credential(
                representation.getType(),
                representation.getUserLabel(),
                this.unmodeledAttributes.extract(representation, CREDENTIAL_NOT_UNMODELED));
    }

    private CredentialRepresentation toRepresentation(final Credential credential) {
        CredentialRepresentation representation = new CredentialRepresentation();
        this.unmodeledAttributes.apply(credential.unmodeled(), representation);
        representation.setType(credential.type());
        representation.setUserLabel(credential.userLabel());
        return representation;
    }

    private static Map<String, Set<String>> toRoleSets(final Map<String, List<String>> roles) {
        Map<String, Set<String>> sets = new LinkedHashMap<>();
        roles.forEach((clientId, names) -> sets.put(clientId, names == null ? Set.of() : new LinkedHashSet<>(names)));
        return sets;
    }
}
