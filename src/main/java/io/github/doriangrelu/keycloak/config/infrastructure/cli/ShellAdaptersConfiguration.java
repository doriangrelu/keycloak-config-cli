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

import io.github.doriangrelu.keycloak.config.application.definition.ConnectTarget;
import io.github.doriangrelu.keycloak.config.application.definition.CreateClient;
import io.github.doriangrelu.keycloak.config.application.definition.CreateGroup;
import io.github.doriangrelu.keycloak.config.application.definition.CreateRealm;
import io.github.doriangrelu.keycloak.config.application.definition.CreateRole;
import io.github.doriangrelu.keycloak.config.application.definition.EditClient;
import io.github.doriangrelu.keycloak.config.application.definition.EditGroup;
import io.github.doriangrelu.keycloak.config.application.definition.EditRole;
import io.github.doriangrelu.keycloak.config.application.definition.FormatRealm;
import io.github.doriangrelu.keycloak.config.application.definition.ListVariables;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.ReorganizeRealm;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.SaveElement;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakBuiltIns;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.application.definition.port.Targets;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.DefinedRolesRule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.UniqueIdentifierRule;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.shell.ShellProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection.ChildContextKeycloakConnector;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection.TargetImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.source.definition.RealmDefinitionFiles;
import io.github.doriangrelu.keycloak.config.infrastructure.source.rules.BaselineBuiltIns;
import io.github.doriangrelu.keycloak.config.infrastructure.source.rules.RuleFilesOnDisk;
import io.github.doriangrelu.keycloak.config.infrastructure.source.targets.TargetsFile;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Assembles the use cases of the shell on the definition files (ADR-008). The shell itself only sees the application:
 * the adapters are given to it here.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "SHELL")
public class ShellAdaptersConfiguration {

    @Bean
    public RealmDefinitions realmDefinitions(final ShellProperties properties) {
        return new RealmDefinitionFiles(properties.getRoot());
    }

    @Bean
    public RuleFiles ruleFiles(final ShellProperties properties) {
        return new RuleFilesOnDisk(properties.getRules());
    }

    @Bean
    public Targets targets(final ShellProperties properties) {
        return new TargetsFile(properties.getRoot());
    }

    /**
     * One Spring context per connection, built on a copy of the environment of the application, importing with the
     * default options of the command-line import.
     */
    @Bean
    public KeycloakConnector keycloakConnector(final ConfigurableEnvironment environment) {
        return new ChildContextKeycloakConnector(environment, defaultImportOptions());
    }

    /**
     * The options of an import from the shell: the default values of the {@code import.*} properties, which the shell
     * does not let change ({@code import.validate}, {@code import.behaviors.*}).
     */
    public static TargetImportOptions defaultImportOptions() {
        ImportConfigProperties defaults =
                new Binder().bindOrCreate("import", Bindable.of(ImportConfigProperties.class));
        return new TargetImportOptions(
                ImportCommandConfiguration.keycloakImportOptionsOf(defaults),
                defaults.getBehaviors().isSyncUserFederation());
    }

    @Bean
    public ConnectTarget connectTarget(final Targets targets, final KeycloakConnector keycloakConnector) {
        return new ConnectTarget(targets, keycloakConnector);
    }

    @Bean
    public KeycloakBuiltIns keycloakBuiltIns(@Value("${keycloak.version}") final String keycloakVersion) {
        return new BaselineBuiltIns(keycloakVersion);
    }

    @Bean
    public RuleBook ruleBook(final RuleFiles ruleFiles, final KeycloakBuiltIns keycloakBuiltIns) {
        return new RuleBook(
                ruleFiles,
                List.of(
                        new UniqueIdentifierRule(RuleElement.CLIENT),
                        new UniqueIdentifierRule(RuleElement.ROLE),
                        new UniqueIdentifierRule(RuleElement.GROUP),
                        new DefinedRolesRule()),
                keycloakBuiltIns);
    }

    @Bean
    public SaveElement saveElement(final RealmDefinitions realmDefinitions, final RuleBook ruleBook) {
        return new SaveElement(realmDefinitions, ruleBook);
    }

    @Bean
    public CreateClient createClient(final RealmDefinitions realmDefinitions, final SaveElement saveElement) {
        return new CreateClient(realmDefinitions, saveElement);
    }

    @Bean
    public EditClient editClient(final RealmDefinitions realmDefinitions, final SaveElement saveElement) {
        return new EditClient(realmDefinitions, saveElement);
    }

    @Bean
    public CreateRole createRole(
            final RealmDefinitions realmDefinitions, final SaveElement saveElement, final RuleBook ruleBook) {
        return new CreateRole(realmDefinitions, saveElement, ruleBook);
    }

    @Bean
    public EditRole editRole(final RealmDefinitions realmDefinitions, final SaveElement saveElement) {
        return new EditRole(realmDefinitions, saveElement);
    }

    @Bean
    public CreateGroup createGroup(
            final RealmDefinitions realmDefinitions, final SaveElement saveElement, final RuleBook ruleBook) {
        return new CreateGroup(realmDefinitions, saveElement, ruleBook);
    }

    @Bean
    public EditGroup editGroup(
            final RealmDefinitions realmDefinitions, final SaveElement saveElement, final RuleBook ruleBook) {
        return new EditGroup(realmDefinitions, saveElement, ruleBook);
    }

    @Bean
    public ReadRealms readRealms(final RealmDefinitions realmDefinitions, final RuleBook ruleBook) {
        return new ReadRealms(realmDefinitions, ruleBook);
    }

    @Bean
    public FormatRealm formatRealm(final RealmDefinitions realmDefinitions) {
        return new FormatRealm(realmDefinitions);
    }

    @Bean
    public ReorganizeRealm reorganizeRealm(final RealmDefinitions realmDefinitions) {
        return new ReorganizeRealm(realmDefinitions);
    }

    @Bean
    public CreateRealm createRealm(final RealmDefinitions realmDefinitions, final RuleBook ruleBook) {
        return new CreateRealm(realmDefinitions, ruleBook);
    }

    @Bean
    public ListVariables listVariables(final RealmDefinitions realmDefinitions) {
        return new ListVariables(realmDefinitions);
    }
}
