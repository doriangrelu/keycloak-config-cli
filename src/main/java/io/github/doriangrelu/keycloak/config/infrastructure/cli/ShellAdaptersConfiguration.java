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

import io.github.doriangrelu.keycloak.config.application.definition.CreateRealm;
import io.github.doriangrelu.keycloak.config.application.definition.FormatRealm;
import io.github.doriangrelu.keycloak.config.application.definition.ListVariables;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.shell.ShellProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.source.definition.RealmDefinitionFiles;
import io.github.doriangrelu.keycloak.config.infrastructure.source.rules.BaselineBuiltIns;
import io.github.doriangrelu.keycloak.config.infrastructure.source.rules.RuleFilesOnDisk;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public RuleBook ruleBook(@Value("${keycloak.version}") final String keycloakVersion) {
        return new RuleBook(new RuleFilesOnDisk(Optional.empty()), List.of(), new BaselineBuiltIns(keycloakVersion));
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
    public CreateRealm createRealm(final RealmDefinitions realmDefinitions, final RuleBook ruleBook) {
        return new CreateRealm(realmDefinitions, ruleBook);
    }

    @Bean
    public ListVariables listVariables(final RealmDefinitions realmDefinitions) {
        return new ListVariables(realmDefinitions);
    }
}
