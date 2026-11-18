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

import io.github.doriangrelu.keycloak.config.application.messagebundle.MessageBundleReconciler;
import io.github.doriangrelu.keycloak.config.application.messagebundle.port.MessageBundleGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.requiredaction.RequiredActionReconciler;
import io.github.doriangrelu.keycloak.config.application.requiredaction.port.RequiredActionGateway;
import io.github.doriangrelu.keycloak.config.application.role.ClientRoleReconciler;
import io.github.doriangrelu.keycloak.config.application.role.RealmRoleReconciler;
import io.github.doriangrelu.keycloak.config.application.role.RoleCompositeReconciler;
import io.github.doriangrelu.keycloak.config.application.role.port.RoleGateway;
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
            final MessageBundleGateway messageBundleGateway) {
        return new ReconciliationEngine(List.of(
                new RealmRoleReconciler(roleGateway),
                new ClientRoleReconciler(roleGateway),
                new RoleCompositeReconciler(roleGateway),
                new RequiredActionReconciler(requiredActionGateway),
                new MessageBundleReconciler(messageBundleGateway)));
    }
}
