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

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.importation.ImportOptions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.application.importation.port.RealmDeclarationParser;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.source.FileLocations;
import io.github.doriangrelu.keycloak.config.infrastructure.source.ImportFileReader;
import io.github.doriangrelu.keycloak.config.infrastructure.source.MustacheTemplates;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The import command: binds the {@code import.*} properties and turns them into the options of each layer. Neither the
 * application nor the other infrastructure packages see the properties.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
@EnableConfigurationProperties(ImportConfigProperties.class)
public class ImportCommandConfiguration {

    @Bean
    public ImportFileSource importFileSource(
            final PathMatchingResourcePatternResolver patternResolver, final ImportConfigProperties properties) {
        FileLocations locations = new FileLocations(
                patternResolver,
                properties.getFiles().getExcludes(),
                properties.getFiles().isIncludeHiddenFiles());
        Optional<MustacheTemplates> templates = properties.getMustache().isEnabled()
                ? Optional.of(new MustacheTemplates(properties.getMustache().getVariables()))
                : Optional.empty();
        return new ImportFileReader(locations, templates);
    }

    @Bean
    public KeycloakImportOptions keycloakImportOptions(final ImportConfigProperties properties) {
        return keycloakImportOptionsOf(properties);
    }

    /** The options of the import that concern Keycloak itself, read from the {@code import.*} properties. */
    static KeycloakImportOptions keycloakImportOptionsOf(final ImportConfigProperties properties) {
        return new KeycloakImportOptions(
                properties.isValidate(),
                properties.getBehaviors().isRemoveDefaultRoleFromUser(),
                properties.getBehaviors().isSkipAttributesForFederatedUser());
    }

    /** The import use case (ADR-007), on its ports. */
    @Bean
    public ImportRealms importRealms(
            final ImportFileSource importFileSource,
            final RealmDeclarationParser realmDeclarationParser,
            final RealmGateway realmGateway,
            final ComponentGateway componentGateway,
            final ReconciliationEngine reconciliationEngine,
            final ImportConfigProperties properties) {
        return new ImportRealms(
                importFileSource,
                realmDeclarationParser,
                realmGateway,
                componentGateway,
                reconciliationEngine,
                new ImportOptions(
                        properties.isDryRun(), properties.getBehaviors().isSyncUserFederation()));
    }
}
