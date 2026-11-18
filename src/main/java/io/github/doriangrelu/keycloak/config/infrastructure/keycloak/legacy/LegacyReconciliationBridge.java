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
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.requiredaction.RequiredActionReconciler;
import io.github.doriangrelu.keycloak.config.application.role.ClientRoleReconciler;
import io.github.doriangrelu.keycloak.config.application.role.RealmRoleReconciler;
import io.github.doriangrelu.keycloak.config.domain.common.ManagedMode;
import io.github.doriangrelu.keycloak.config.domain.common.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.clientscope.ClientScopeKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction.RequiredActionKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.role.RoleKeycloakMapper;
import io.github.doriangrelu.keycloak.config.model.ProtectedResource;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues;
import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import io.github.doriangrelu.keycloak.config.service.state.StateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.representations.idm.RolesRepresentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Transitional glue between the legacy import, which works file by file on {@link RealmImport}, and the
 * reconciliation engine, for the resource types already migrated (EPIC-002). Removed when the import runs on the
 * engine only (EPIC-003).
 *
 * <p>The {@link RealmContext} of each realm is kept in the legacy execution context of the run: only this class
 * reads it, and passes it explicitly to the engine.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class LegacyReconciliationBridge {

    private final ReconciliationEngine engine;
    private final RoleKeycloakMapper roleMapper;
    private final ClientScopeKeycloakMapper clientScopeMapper;
    private final RequiredActionKeycloakMapper requiredActionMapper;
    private final StateService stateService;
    private final ImportConfigProperties properties;

    public LegacyReconciliationBridge(
            final ReconciliationEngine engine,
            final RoleKeycloakMapper roleMapper,
            final ClientScopeKeycloakMapper clientScopeMapper,
            final RequiredActionKeycloakMapper requiredActionMapper,
            final StateService stateService,
            final ImportConfigProperties properties) {
        this.engine = engine;
        this.roleMapper = roleMapper;
        this.clientScopeMapper = clientScopeMapper;
        this.requiredActionMapper = requiredActionMapper;
        this.stateService = stateService;
        this.properties = properties;
    }

    /**
     * Prepares the import of one file: captures the remote state before the import overwrites it, and adds what the
     * file declares to the desired state of its realm. Called for every file, including those whose import is
     * skipped because their checksum did not change.
     *
     * @param remoteStateLoaded whether the remote state of the realm was loaded for this file
     */
    public void startFile(final RealmImport realmImport, final boolean remoteStateLoaded) {
        RealmContext context = contextOf(realmImport);
        context.addProtections(protectionsOf(realmImport));
        if (remoteStateLoaded) {
            captureRoleState(realmImport, context);
            if (realmImport.getRequiredActions() != null) {
                context.captureState(RequiredActionReconciler.STATE_SCOPE, this.stateService.getRequiredActions());
            }
            if (realmImport.getMessageBundles() != null) {
                context.captureState(MessageBundleReconciler.STATE_SCOPE, this.stateService.getMessageBundles());
            }
        }
        this.engine.register(context, desiredStateOf(realmImport));
    }

    /**
     * Creates and updates what one file declares for some of the migrated types. The legacy import calls it at the
     * place each type had, so that the types it still handles find what they rely on.
     */
    public void apply(final RealmImport realmImport, final Set<ResourceType> types) {
        this.engine.apply(contextOf(realmImport), desiredStateOf(realmImport), types);
    }

    /** Deletes, once per realm, the managed resources of the migrated types missing from every file of the realm. */
    public void prune(final RealmImport realmImport) {
        this.engine.prune(contextOf(realmImport));
    }

    private synchronized RealmContext contextOf(final RealmImport realmImport) {
        String realm = realmImport.getRealm();
        return ExecutionContextHolder.context().get(realm, RealmContext.class).stream()
                .findFirst()
                .orElseGet(() -> {
                    RealmContext created = new RealmContext(
                            realm,
                            this.properties.isParallel(),
                            this.properties.getRemoteState().isEnabled(),
                            Map.of(
                                    ResourceType.REALM_ROLE, roleMode(),
                                    ResourceType.CLIENT_ROLE, roleMode(),
                                    ResourceType.REQUIRED_ACTION, requiredActionMode(),
                                    ResourceType.MESSAGE_BUNDLE, messageBundleMode(),
                                    ResourceType.CLIENT_SCOPE, clientScopeMode()));
                    ExecutionContextHolder.context().put(realm, RealmContext.class, List.of(created));
                    return created;
                });
    }

    private ManagedMode clientScopeMode() {
        return this.properties.getManaged().getClientScope() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode messageBundleMode() {
        return this.properties.getManaged().getMessageBundles() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode requiredActionMode() {
        return this.properties.getManaged().getRequiredAction() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private ManagedMode roleMode() {
        return this.properties.getManaged().getRole() == ImportManagedPropertiesValues.FULL
                ? ManagedMode.FULL
                : ManagedMode.NO_DELETE;
    }

    private void captureRoleState(final RealmImport realmImport, final RealmContext context) {
        RolesRepresentation roles = realmImport.getRoles();
        if (roles == null) {
            return;
        }
        if (roles.getRealm() != null) {
            context.captureState(RealmRoleReconciler.STATE_SCOPE, this.stateService.getRealmRoles());
        }
        if (roles.getClient() != null) {
            roles.getClient()
                    .keySet()
                    .forEach(clientId -> context.captureState(
                            ClientRoleReconciler.stateScope(clientId), this.stateService.getClientRoles(clientId)));
        }
    }

    private DesiredState desiredStateOf(final RealmImport realmImport) {
        DesiredState desired = new DesiredState();
        if (realmImport.getClientScopes() != null) {
            desired.declare(
                    ResourceType.CLIENT_SCOPE,
                    realmImport.getClientScopes().stream()
                            .map(scope -> this.clientScopeMapper.toClientScope(scope, false))
                            .toList());
        }
        if (realmImport.getMessageBundles() != null) {
            desired.declare(
                    ResourceType.MESSAGE_BUNDLE,
                    realmImport.getMessageBundles().entrySet().stream()
                            .map(locale -> new MessageBundle(locale.getKey(), locale.getValue()))
                            .toList());
        }
        if (realmImport.getRequiredActions() != null) {
            desired.declare(
                    ResourceType.REQUIRED_ACTION,
                    realmImport.getRequiredActions().stream()
                            .map(this.requiredActionMapper::toRequiredAction)
                            .toList());
        }
        RolesRepresentation roles = realmImport.getRoles();
        if (roles == null) {
            return desired;
        }
        if (roles.getRealm() != null) {
            desired.declare(
                    ResourceType.REALM_ROLE,
                    roles.getRealm().stream()
                            .map(role -> this.roleMapper.toRole(role, null))
                            .toList());
        }
        List<RoleComposites> composites = new ArrayList<>();
        if (roles.getRealm() != null) {
            roles.getRealm()
                    .forEach(role -> this.roleMapper.toComposites(role, null).ifPresent(composites::add));
        }
        if (roles.getClient() != null) {
            List<Role> clientRoles = new ArrayList<>();
            roles.getClient().forEach((clientId, declared) -> {
                if (declared != null) {
                    declared.forEach(role -> {
                        clientRoles.add(this.roleMapper.toRole(role, clientId));
                        this.roleMapper.toComposites(role, clientId).ifPresent(composites::add);
                    });
                }
            });
            desired.declare(
                    ResourceType.CLIENT_ROLE, clientRoles, roles.getClient().keySet());
        }
        if (!composites.isEmpty()) {
            desired.declare(ResourceType.ROLE_COMPOSITE, composites);
        }
        return desired;
    }

    private static List<ProtectionRule> protectionsOf(final RealmImport realmImport) {
        return realmImport.getProtectedResources().stream()
                .map(LegacyReconciliationBridge::toRule)
                .toList();
    }

    private static ProtectionRule toRule(final ProtectedResource resource) {
        ProtectionRule.Matching matching = resource.rule() == ProtectedResource.Rule.PATTERN
                ? ProtectionRule.Matching.PATTERN
                : ProtectionRule.Matching.EQUALS;
        return new ProtectionRule(matching, resource.matcher());
    }
}
