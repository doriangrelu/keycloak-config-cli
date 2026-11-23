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

package io.github.doriangrelu.keycloak.config.application.importation;

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.application.importation.port.RealmDeclarationParser;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the import files to Keycloak, like {@code kubectl apply} applies manifests (ADR-007).
 *
 * <ol>
 *   <li>Every file is read and parsed first: an invalid file changes nothing.
 *   <li>Each realm a file declares is created or updated, then what the file declares is created and updated, type
 *       after type, in the order of their dependencies.
 *   <li>Once every file is applied, the clean phase of each realm deletes what no file of the realm declares, for the
 *       declared types only, except the users, the resources of Keycloak and the protected resources.
 * </ol>
 *
 * <p>In a dry run, nothing is written: the changes are only planned, and returned in the report.
 */
public final class ImportRealms {

    /** The types a file is applied with, step by step: each step relies on what the previous ones created. */
    private static final List<Set<ResourceType>> STEPS = List.of(
            Set.of(ResourceType.CLIENT_SCOPE),
            Set.of(ResourceType.DEFAULT_CLIENT_SCOPE),
            Set.of(ResourceType.CLIENT_PROFILE, ResourceType.CLIENT_POLICY),
            Set.of(ResourceType.CLIENT),
            Set.of(
                    ResourceType.REALM_ROLE,
                    ResourceType.CLIENT_ROLE,
                    ResourceType.ROLE_COMPOSITE,
                    ResourceType.REQUIRED_ACTION,
                    ResourceType.MESSAGE_BUNDLE,
                    ResourceType.GROUP),
            Set.of(ResourceType.DEFAULT_GROUP),
            Set.of(ResourceType.COMPONENT, ResourceType.SUB_COMPONENT));

    /** The steps after the user storages, whose users may be synchronized before the users are applied. */
    private static final List<Set<ResourceType>> STEPS_AFTER_USER_STORAGES = List.of(
            Set.of(ResourceType.USER_PROFILE),
            Set.of(ResourceType.USER),
            Set.of(ResourceType.AUTHENTICATION_FLOW),
            Set.of(ResourceType.REALM_FLOW_BINDING),
            Set.of(ResourceType.AUTHENTICATOR_CONFIG),
            Set.of(ResourceType.CLIENT_FLOW_BINDING),
            // The default client scopes a second time, once every resource of the file exists
            Set.of(ResourceType.DEFAULT_CLIENT_SCOPE),
            Set.of(ResourceType.IDENTITY_PROVIDER, ResourceType.IDENTITY_PROVIDER_MAPPER),
            Set.of(ResourceType.CLIENT_AUTHORIZATION),
            Set.of(ResourceType.SCOPE_MAPPING, ResourceType.CLIENT_SCOPE_MAPPING));

    private final ImportFileSource files;
    private final RealmDeclarationParser parser;
    private final RealmGateway realms;
    private final ComponentGateway components;
    private final ReconciliationEngine engine;
    private final ImportOptions options;

    public ImportRealms(
            final ImportFileSource files,
            final RealmDeclarationParser parser,
            final RealmGateway realms,
            final ComponentGateway components,
            final ReconciliationEngine engine,
            final ImportOptions options) {
        this.files = Objects.requireNonNull(files, "files");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.realms = Objects.requireNonNull(realms, "realms");
        this.components = Objects.requireNonNull(components, "components");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Applies the files of the given locations, then cleans each realm.
     *
     * @throws InvalidImportException when a file cannot be read or parsed; nothing is changed then
     */
    public ImportReport importFiles(final Collection<String> locations) {
        List<RealmDeclaration> declarations = new ArrayList<>();
        for (ImportFile file : this.files.read(locations)) {
            declarations.addAll(this.parser.parse(file));
        }
        return importRealms(declarations);
    }

    /** Applies the declarations in their order, then cleans each realm. */
    public ImportReport importRealms(final List<RealmDeclaration> declarations) {
        Map<String, RealmContext> contexts = apply(declarations);
        contexts.values().forEach(this.engine::prune);
        return reportOf(contexts);
    }

    /**
     * Applies the declarations without the clean phase: nothing is deleted. Meant for a partial import, such as a test
     * of one file.
     */
    public ImportReport importRealmsWithoutCleanup(final List<RealmDeclaration> declarations) {
        return reportOf(apply(declarations));
    }

    private Map<String, RealmContext> apply(final List<RealmDeclaration> declarations) {
        // One context per realm and run: the clean phase relies on what every file of the realm declared
        Map<String, RealmContext> contexts = new LinkedHashMap<>();
        for (RealmDeclaration declaration : declarations) {
            RealmContext context = contexts.computeIfAbsent(
                    declaration.realm(), realm -> new RealmContext(realm, this.options.dryRun()));
            apply(context, declaration);
        }
        return contexts;
    }

    private void apply(final RealmContext context, final RealmDeclaration declaration) {
        boolean realmExists = this.realms.findSettings(declaration.realm()).isPresent();
        if (!realmExists && context.isDryRun()) {
            // A dry run never creates the realm: nothing can be read from it
            context.realmIsMissing();
        }
        context.addProtections(declaration.protections());
        this.engine.register(context, declaration.desiredState(realmExists));

        // The realm settings come first: they create the realm when it does not exist
        this.engine.apply(context, declaration.settings(), Set.of(ResourceType.REALM_SETTINGS));
        DesiredState desired = declaration.desiredState(!context.isRealmMissing());
        STEPS.forEach(types -> this.engine.apply(context, desired, types));
        if (!context.isDryRun() && this.options.syncUserFederation() && declaration.declaresUserStorage()) {
            this.components.syncImportingUserStorages(declaration.realm());
        }
        STEPS_AFTER_USER_STORAGES.forEach(types -> this.engine.apply(context, desired, types));
    }

    private static ImportReport reportOf(final Map<String, RealmContext> contexts) {
        Map<String, List<PlannedChange>> plans = new LinkedHashMap<>();
        contexts.forEach((realm, context) -> plans.put(realm, context.plan()));
        return new ImportReport(plans);
    }
}
