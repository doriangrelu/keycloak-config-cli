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

import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.stereotype.Component;

/** Conversions between the Keycloak representation of a realm and its settings (ADR-006). */
@Component
public class RealmKeycloakMapper {

    /** Top-level fields of an import that are not settings: imported on their own, or after the flows exist. */
    public static final Set<String> NOT_SETTINGS = Set.of(
            "authenticatorConfig",
            "clients",
            "roles",
            "users",
            "groups",
            "defaultGroups",
            "identityProviders",
            "identityProviderMappers",
            "browserFlow",
            "directGrantFlow",
            "clientAuthenticationFlow",
            "dockerAuthenticationFlow",
            "registrationFlow",
            "resetCredentialsFlow",
            "components",
            "authenticationFlows",
            "scopeMappings",
            "clientScopeMappings",
            "clientScopes",
            "requiredActions",
            "defaultDefaultClientScopes",
            "defaultOptionalClientScopes",
            "clientProfiles",
            "clientPolicies");

    /** Fields of a realm that are not settings either: its identity. */
    private static final Set<String> IDENTITY = Set.of("id", "realm");

    private static final Set<String> NOT_UNMODELED =
            Stream.concat(NOT_SETTINGS.stream(), IDENTITY.stream()).collect(Collectors.toSet());

    private final UnmodeledAttributesConverter unmodeledAttributes;

    public RealmKeycloakMapper(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    /** Settings of a realm declared by an import file, or read from Keycloak. */
    public RealmSettings toSettings(final RealmRepresentation representation) {
        return new RealmSettings(
                representation.getRealm(), this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    /** Settings read from Keycloak, with the defaults Keycloak omits. */
    public RealmSettings toCurrentSettings(final RealmRepresentation representation) {
        if (representation.getAttributes() == null) {
            representation.setAttributes(new HashMap<>());
        }
        if (representation.isEventsEnabled() == null) {
            representation.setEventsEnabled(false);
        }
        return toSettings(representation);
    }

    public RealmRepresentation toRepresentation(final RealmSettings settings) {
        RealmRepresentation representation = new RealmRepresentation();
        this.unmodeledAttributes.apply(settings.settings(), representation);
        representation.setRealm(settings.realm());
        return representation;
    }
}
