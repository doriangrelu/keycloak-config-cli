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

package io.github.doriangrelu.keycloak.config.infrastructure.cli.shell;

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
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import java.io.PrintWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/** The shell command (ADR-008): a second input adapter, next to the import and the normalization. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "SHELL")
@EnableConfigurationProperties(ShellProperties.class)
public class ShellCommandConfiguration {

    @Bean
    public ShellSession shellSession() {
        return new ShellSession();
    }

    @Bean
    public RealmCommands realmCommands(
            final ReadRealms readRealms,
            final FormatRealm formatRealm,
            final CreateRealm createRealm,
            final ShellSession shellSession) {
        return new RealmCommands(readRealms, formatRealm, createRealm, shellSession);
    }

    @Bean
    public VariableCommands variableCommands(final ListVariables listVariables, final ShellSession shellSession) {
        return new VariableCommands(listVariables, shellSession);
    }

    @Bean
    public RuleCommands ruleCommands(final RuleBook ruleBook) {
        return new RuleCommands(ruleBook);
    }

    @Bean
    public ClientCommands clientCommands(
            final CreateClient createClient,
            final EditClient editClient,
            final ReadRealms readRealms,
            final ShellSession shellSession) {
        return new ClientCommands(createClient, editClient, readRealms, shellSession);
    }

    @Bean
    public RoleCommands roleCommands(
            final CreateRole createRole,
            final EditRole editRole,
            final ReadRealms readRealms,
            final ShellSession shellSession) {
        return new RoleCommands(createRole, editRole, readRealms, shellSession);
    }

    @Bean
    public GroupCommands groupCommands(
            final CreateGroup createGroup,
            final EditGroup editGroup,
            final ReadRealms readRealms,
            final ShellSession shellSession) {
        return new GroupCommands(createGroup, editGroup, readRealms, shellSession);
    }

    /** Named as the runner of Spring Shell, which is then not registered. */
    @Bean
    public ShellLauncher springShellApplicationRunner(
            final ShellProperties properties,
            final RuleBook ruleBook,
            final ReadRealms readRealms,
            final ShellSession shellSession,
            final CommandParser commandParser,
            final CommandRegistry commandRegistry,
            final ObjectProvider<ShellRunner> interactiveShell) {
        return new ShellLauncher(
                properties,
                ruleBook,
                readRealms,
                shellSession,
                commandParser,
                commandRegistry,
                interactiveShell,
                new PrintWriter(System.out, true));
    }
}
