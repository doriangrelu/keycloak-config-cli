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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.authenticationflow.AuthenticationFlowKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientpolicy.ClientPolicyKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.component.ComponentKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.group.GroupKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.identityprovider.IdentityProviderKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.UnmodeledAttributesConverter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.realm.RealmKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction.RequiredActionKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role.RoleKeycloakMapper;
import org.springframework.context.annotation.Bean;

/**
 * The normalization in the Spring context of a target, for the extraction of a realm (ADR-010): the reader, the
 * writer, the reference realms and the normalizer, built on the mappers of the import. The command line of the
 * normalization is not there: no runner, no parser of export files, no writer of files.
 *
 * <p>No reference version is given: {@link RealmExportNormalizer#normalize(org.keycloak.representations.idm.RealmRepresentation,
 * String)} is called with the version of the target, and {@link BaselineProvider} falls back on the closest known
 * version. This class carries no {@code @Configuration}, so that component scanning never picks it up in the main
 * context: the context of a target imports it.
 */
public class TargetNormalizationConfiguration {

    private static final NormalizationOptions NO_OPTION = new NormalizationOptions(null, null);

    @Bean
    BaselineProvider extractionBaselineProvider() {
        return new BaselineProvider(new ObjectMapper(), NO_OPTION);
    }

    @Bean
    @SuppressWarnings("java:S107") // One mapper per type of resource
    RealmExportReader extractionRealmReader(
            final RealmKeycloakMapper realmMapper,
            final ClientKeycloakMapper clientMapper,
            final ClientPolicyKeycloakMapper clientPolicyMapper,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final IdentityProviderKeycloakMapper identityProviderMapper,
            final AuthenticationFlowKeycloakMapper authenticationFlowMapper,
            final ComponentKeycloakMapper componentMapper,
            final GroupKeycloakMapper groupMapper,
            final RequiredActionKeycloakMapper requiredActionMapper,
            final RoleKeycloakMapper roleMapper,
            final UnmodeledAttributesConverter unmodeledAttributes) {
        return new RealmExportReader(
                realmMapper,
                clientMapper,
                clientPolicyMapper,
                clientScopeMapper,
                identityProviderMapper,
                authenticationFlowMapper,
                componentMapper,
                groupMapper,
                requiredActionMapper,
                roleMapper,
                unmodeledAttributes);
    }

    @Bean
    @SuppressWarnings("java:S107") // One mapper per type of resource
    RealmExportWriter extractionRealmWriter(
            final RealmKeycloakMapper realmMapper,
            final ClientKeycloakMapper clientMapper,
            final ClientPolicyKeycloakMapper clientPolicyMapper,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final IdentityProviderKeycloakMapper identityProviderMapper,
            final AuthenticationFlowKeycloakMapper authenticationFlowMapper,
            final GroupKeycloakMapper groupMapper,
            final RequiredActionKeycloakMapper requiredActionMapper,
            final RoleKeycloakMapper roleMapper,
            final UnmodeledAttributesConverter unmodeledAttributes) {
        return new RealmExportWriter(
                realmMapper,
                clientMapper,
                clientPolicyMapper,
                clientScopeMapper,
                identityProviderMapper,
                authenticationFlowMapper,
                groupMapper,
                requiredActionMapper,
                roleMapper,
                unmodeledAttributes);
    }

    @Bean
    ReferenceRealmKeycloakGateway extractionReferenceRealms(
            final BaselineProvider baselineProvider,
            final RealmExportReader reader,
            final ClientKeycloakMapper clientMapper) {
        return new ReferenceRealmKeycloakGateway(baselineProvider, reader, clientMapper);
    }

    @Bean
    RealmExportNormalizer extractionNormalizer(
            final RealmExportReader reader,
            final RealmExportWriter writer,
            final ReferenceRealmKeycloakGateway referenceRealms) {
        return new RealmExportNormalizer(NO_OPTION, reader, writer, referenceRealms);
    }
}
