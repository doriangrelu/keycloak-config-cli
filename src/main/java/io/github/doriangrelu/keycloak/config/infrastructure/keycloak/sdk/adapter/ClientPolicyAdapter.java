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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientPolicyApi;
import java.util.List;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientPoliciesRepresentation;
import org.keycloak.representations.idm.ClientPolicyRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;

/** {@link ClientPolicyApi} on the Keycloak Admin client. */
public class ClientPolicyAdapter extends AbstractAdapter implements ClientPolicyApi {

    private static final boolean WITHOUT_GLOBAL_PROFILES = false;

    public ClientPolicyAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<ClientProfileRepresentation> listProfiles(final String realm) {
        ClientProfilesRepresentation profiles = execute(
                () -> keycloak().realm(realm).clientPoliciesProfilesResource().getProfiles(WITHOUT_GLOBAL_PROFILES));
        return profiles == null || profiles.getProfiles() == null ? List.of() : profiles.getProfiles();
    }

    @Override
    public void replaceProfiles(final String realm, final List<ClientProfileRepresentation> profiles) {
        ClientProfilesRepresentation representation = new ClientProfilesRepresentation();
        representation.setProfiles(profiles);
        execute(() -> keycloak().realm(realm).clientPoliciesProfilesResource().updateProfiles(representation));
    }

    @Override
    public List<ClientPolicyRepresentation> listPolicies(final String realm) {
        ClientPoliciesRepresentation policies = execute(
                () -> keycloak().realm(realm).clientPoliciesPoliciesResource().getPolicies());
        return policies == null || policies.getPolicies() == null ? List.of() : policies.getPolicies();
    }

    @Override
    public void replacePolicies(final String realm, final List<ClientPolicyRepresentation> policies) {
        ClientPoliciesRepresentation representation = new ClientPoliciesRepresentation();
        representation.setPolicies(policies);
        execute(() -> keycloak().realm(realm).clientPoliciesPoliciesResource().updatePolicies(representation));
    }
}
