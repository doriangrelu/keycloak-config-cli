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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportOptions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.RealmDeclaration;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.application.importation.port.RealmDeclarationParser;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A connection held by the Spring context of its target, where the import lives: closing the connection closes the
 * context.
 */
final class TargetConnection implements Connection {

    /** The files are given to the import: it never reads a location. */
    private static final ImportFileSource NO_LOCATION = locations -> {
        throw new UnsupportedOperationException("The import of a target is given its files");
    };

    private final Target target;
    private final String keycloakVersion;
    private final ConfigurableApplicationContext context;
    private final boolean syncUserFederation;

    TargetConnection(
            final Target target,
            final String keycloakVersion,
            final ConfigurableApplicationContext context,
            final boolean syncUserFederation) {
        this.target = target;
        this.keycloakVersion = keycloakVersion;
        this.context = context;
        this.syncUserFederation = syncUserFederation;
    }

    @Override
    public Target target() {
        return this.target;
    }

    @Override
    public String keycloakVersion() {
        return this.keycloakVersion;
    }

    /** The Spring context of the target, where the Keycloak client and the import live. */
    ConfigurableApplicationContext context() {
        return this.context;
    }

    @Override
    public ImportReport dryRun(final List<ImportFile> files) {
        return importOf(files, true);
    }

    @Override
    public ImportReport apply(final List<ImportFile> files) {
        return importOf(files, false);
    }

    /**
     * The import of the command line on the beans of the context, with the options of this call: the files are all
     * parsed first, applied in their order, then each realm is cleaned.
     */
    private ImportReport importOf(final List<ImportFile> files, final boolean dryRun) {
        Objects.requireNonNull(files, "files");
        try {
            RealmDeclarationParser parser = this.context.getBean(RealmDeclarationParser.class);
            List<RealmDeclaration> declarations = new ArrayList<>();
            for (ImportFile file : files) {
                declarations.addAll(parser.parse(file));
            }
            ImportRealms importRealms = new ImportRealms(
                    NO_LOCATION,
                    parser,
                    this.context.getBean(RealmGateway.class),
                    this.context.getBean(ComponentGateway.class),
                    this.context.getBean(ReconciliationEngine.class),
                    new ImportOptions(dryRun, this.syncUserFederation));
            return importRealms.importRealms(declarations);
        } catch (RuntimeException failure) {
            throw KeycloakFailures.duringImport(this.target, failure);
        }
    }

    @Override
    public void close() {
        this.context.close();
    }
}
