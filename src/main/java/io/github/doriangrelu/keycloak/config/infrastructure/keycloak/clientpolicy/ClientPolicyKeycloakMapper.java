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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy;

import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientPolicy;
import io.github.doriangrelu.keycloak.config.domain.clientpolicy.model.ClientProfile;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import java.util.Set;
import org.keycloak.representations.idm.ClientPolicyRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;
import org.springframework.stereotype.Component;

/** Conversions between the Keycloak representations of client profiles and policies and their business objects. */
@Component
public class ClientPolicyKeycloakMapper {

    private static final Set<String> NOT_UNMODELED = Set.of("name");

    private final UnmodeledAttributesConverter unmodeledAttributes;

    public ClientPolicyKeycloakMapper(final UnmodeledAttributesConverter unmodeledAttributes) {
        this.unmodeledAttributes = unmodeledAttributes;
    }

    public ClientProfile toProfile(final ClientProfileRepresentation representation) {
        return new ClientProfile(
                representation.getName(), this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    public ClientProfileRepresentation toRepresentation(final ClientProfile profile) {
        ClientProfileRepresentation representation = new ClientProfileRepresentation();
        this.unmodeledAttributes.apply(profile.unmodeled(), representation);
        representation.setName(profile.name());
        return representation;
    }

    public ClientPolicy toPolicy(final ClientPolicyRepresentation representation) {
        return new ClientPolicy(
                representation.getName(), this.unmodeledAttributes.extract(representation, NOT_UNMODELED));
    }

    public ClientPolicyRepresentation toRepresentation(final ClientPolicy policy) {
        ClientPolicyRepresentation representation = new ClientPolicyRepresentation();
        this.unmodeledAttributes.apply(policy.unmodeled(), representation);
        representation.setName(policy.name());
        return representation;
    }
}
