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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.legacy;

import io.github.doriangrelu.keycloak.config.application.authenticationflow.AuthenticationFlowReconciler;
import io.github.doriangrelu.keycloak.config.application.authenticationflow.AuthenticatorConfigReconciler;
import io.github.doriangrelu.keycloak.config.application.authenticationflow.ClientFlowBindingsReconciler;
import io.github.doriangrelu.keycloak.config.application.authenticationflow.port.AuthenticationFlowGateway;
import io.github.doriangrelu.keycloak.config.application.client.ClientReconciler;
import io.github.doriangrelu.keycloak.config.application.client.port.ClientGateway;
import io.github.doriangrelu.keycloak.config.application.clientauthorization.ClientAuthorizationReconciler;
import io.github.doriangrelu.keycloak.config.application.clientauthorization.port.ClientAuthorizationGateway;
import io.github.doriangrelu.keycloak.config.application.clientpolicy.ClientPolicyReconciler;
import io.github.doriangrelu.keycloak.config.application.clientpolicy.ClientProfileReconciler;
import io.github.doriangrelu.keycloak.config.application.clientpolicy.port.ClientPolicyGateway;
import io.github.doriangrelu.keycloak.config.application.clientscope.ClientScopeReconciler;
import io.github.doriangrelu.keycloak.config.application.clientscope.port.ClientScopeGateway;
import io.github.doriangrelu.keycloak.config.application.component.ComponentReconciler;
import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.group.GroupReconciler;
import io.github.doriangrelu.keycloak.config.application.group.port.GroupGateway;
import io.github.doriangrelu.keycloak.config.application.identityprovider.IdentityProviderMapperReconciler;
import io.github.doriangrelu.keycloak.config.application.identityprovider.IdentityProviderReconciler;
import io.github.doriangrelu.keycloak.config.application.identityprovider.port.IdentityProviderGateway;
import io.github.doriangrelu.keycloak.config.application.messagebundle.MessageBundleReconciler;
import io.github.doriangrelu.keycloak.config.application.messagebundle.port.MessageBundleGateway;
import io.github.doriangrelu.keycloak.config.application.realm.DefaultClientScopesReconciler;
import io.github.doriangrelu.keycloak.config.application.realm.DefaultGroupsReconciler;
import io.github.doriangrelu.keycloak.config.application.realm.RealmSettingsReconciler;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.requiredaction.RequiredActionReconciler;
import io.github.doriangrelu.keycloak.config.application.requiredaction.port.RequiredActionGateway;
import io.github.doriangrelu.keycloak.config.application.role.ClientRoleReconciler;
import io.github.doriangrelu.keycloak.config.application.role.RealmRoleReconciler;
import io.github.doriangrelu.keycloak.config.application.role.RoleCompositeReconciler;
import io.github.doriangrelu.keycloak.config.application.role.port.RoleGateway;
import io.github.doriangrelu.keycloak.config.application.scopemapping.ScopeMappingReconciler;
import io.github.doriangrelu.keycloak.config.application.scopemapping.port.ScopeMappingGateway;
import io.github.doriangrelu.keycloak.config.application.user.UserImportRules;
import io.github.doriangrelu.keycloak.config.application.user.UserReconciler;
import io.github.doriangrelu.keycloak.config.application.user.port.UserGateway;
import io.github.doriangrelu.keycloak.config.application.userprofile.UserProfileReconciler;
import io.github.doriangrelu.keycloak.config.application.userprofile.port.UserProfileGateway;
import io.github.doriangrelu.keycloak.config.domain.common.ManagedMode;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assembles the reconciliation engine and its reconcilers, which are plain Java (ADR-001). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ReconciliationConfiguration {

    @Bean
    public ReconciliationEngine reconciliationEngine(
            final RoleGateway roleGateway,
            final RequiredActionGateway requiredActionGateway,
            final MessageBundleGateway messageBundleGateway,
            final ClientScopeGateway clientScopeGateway,
            final ClientGateway clientGateway,
            final GroupGateway groupGateway,
            final UserGateway userGateway,
            final IdentityProviderGateway identityProviderGateway,
            final ClientPolicyGateway clientPolicyGateway,
            final ScopeMappingGateway scopeMappingGateway,
            final ComponentGateway componentGateway,
            final UserProfileGateway userProfileGateway,
            final AuthenticationFlowGateway authenticationFlowGateway,
            final ClientAuthorizationGateway clientAuthorizationGateway,
            final RealmGateway realmGateway,
            final ImportConfigProperties properties) {
        return new ReconciliationEngine(List.of(
                new RealmSettingsReconciler(realmGateway, ImportConfigProperties.REALM_STATE_ATTRIBUTE_COMMON_PREFIX),
                new DefaultClientScopesReconciler(realmGateway),
                new DefaultGroupsReconciler(realmGateway),
                new RealmRoleReconciler(roleGateway),
                new ClientRoleReconciler(roleGateway),
                new RoleCompositeReconciler(roleGateway),
                new RequiredActionReconciler(requiredActionGateway),
                new MessageBundleReconciler(messageBundleGateway),
                new ClientScopeReconciler(clientScopeGateway),
                new ClientReconciler(clientGateway),
                new GroupReconciler(groupGateway),
                new UserReconciler(
                        userGateway,
                        new UserImportRules(
                                properties.getBehaviors().isRemoveDefaultRoleFromUser(),
                                properties.getBehaviors().isSkipAttributesForFederatedUser())),
                new IdentityProviderReconciler(identityProviderGateway),
                new IdentityProviderMapperReconciler(identityProviderGateway),
                new ClientProfileReconciler(clientPolicyGateway),
                new ClientPolicyReconciler(clientPolicyGateway),
                ScopeMappingReconciler.ofRealmRoles(scopeMappingGateway),
                ScopeMappingReconciler.ofClientRoles(scopeMappingGateway),
                ComponentReconciler.ofComponents(componentGateway),
                ComponentReconciler.ofSubComponents(componentGateway),
                new UserProfileReconciler(userProfileGateway),
                new AuthenticationFlowReconciler(authenticationFlowGateway),
                new AuthenticatorConfigReconciler(authenticationFlowGateway),
                new ClientFlowBindingsReconciler(authenticationFlowGateway),
                new ClientAuthorizationReconciler(
                        clientAuthorizationGateway,
                        new ClientAuthorizationReconciler.Modes(
                                modeOf(properties.getManaged().getClientAuthorizationResources()),
                                modeOf(properties.getManaged().getClientAuthorizationPolicies()),
                                modeOf(properties.getManaged().getClientAuthorizationScopes())))));
    }

    private static ManagedMode modeOf(final ImportManagedPropertiesValues value) {
        return value == ImportManagedPropertiesValues.FULL ? ManagedMode.FULL : ManagedMode.NO_DELETE;
    }
}
