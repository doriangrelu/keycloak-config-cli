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

import io.github.doriangrelu.keycloak.config.application.definition.ConnectTarget;
import io.github.doriangrelu.keycloak.config.application.definition.CreateClient;
import io.github.doriangrelu.keycloak.config.application.definition.CreateGroup;
import io.github.doriangrelu.keycloak.config.application.definition.CreateRealm;
import io.github.doriangrelu.keycloak.config.application.definition.CreateRole;
import io.github.doriangrelu.keycloak.config.application.definition.DeclarationDeletions;
import io.github.doriangrelu.keycloak.config.application.definition.EditClient;
import io.github.doriangrelu.keycloak.config.application.definition.EditGroup;
import io.github.doriangrelu.keycloak.config.application.definition.EditRole;
import io.github.doriangrelu.keycloak.config.application.definition.FormatRealm;
import io.github.doriangrelu.keycloak.config.application.definition.ImportOpenedRealm;
import io.github.doriangrelu.keycloak.config.application.definition.ListVariables;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.ReorganizeRealm;
import io.github.doriangrelu.keycloak.config.application.definition.ResolveVariables;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import java.io.PrintWriter;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.ConsoleInputProvider;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.DefaultCommandParser;

/** The shell command (ADR-008): a second input adapter, next to the import and the normalization. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "SHELL")
@EnableConfigurationProperties(ShellProperties.class)
public class ShellCommandConfiguration {

    /**
     * Created with the first beans, before any command: the loggers that would show the value of a variable stay at
     * {@code INFO} at most.
     */
    @Bean
    public ShellLogLevels shellLogLevels() {
        return ShellLogLevels.pin(LoggingSystem.get(ShellCommandConfiguration.class.getClassLoader()));
    }

    @Bean
    public ShellSession shellSession() {
        return new ShellSession();
    }

    @Bean
    public RealmCommands realmCommands(
            final ReadRealms readRealms,
            final FormatRealm formatRealm,
            final CreateRealm createRealm,
            final ReorganizeRealm reorganizeRealm,
            final ShellSession shellSession) {
        return new RealmCommands(readRealms, formatRealm, createRealm, reorganizeRealm, shellSession);
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
            final ShellSession shellSession,
            final SessionDeclarationWarning sessionDeclarationWarning) {
        return new ClientCommands(createClient, editClient, readRealms, shellSession, sessionDeclarationWarning);
    }

    @Bean
    public RoleCommands roleCommands(
            final CreateRole createRole,
            final EditRole editRole,
            final ReadRealms readRealms,
            final ShellSession shellSession,
            final SessionDeclarationWarning sessionDeclarationWarning) {
        return new RoleCommands(createRole, editRole, readRealms, shellSession, sessionDeclarationWarning);
    }

    @Bean
    public GroupCommands groupCommands(
            final CreateGroup createGroup,
            final EditGroup editGroup,
            final ReadRealms readRealms,
            final ShellSession shellSession,
            final SessionDeclarationWarning sessionDeclarationWarning) {
        return new GroupCommands(createGroup, editGroup, readRealms, shellSession, sessionDeclarationWarning);
    }

    @Bean
    @ConditionalOnMissingBean
    public EnvironmentVariables environmentVariables() {
        return EnvironmentVariables.ofProcess();
    }

    /**
     * The warning of a first declaration: connected, the use case simulates the import on the target, with the values
     * the session knows and {@code KCC_VAR_<name>}; it asks nothing.
     */
    @Bean
    public SessionDeclarationWarning sessionDeclarationWarning(
            final ListVariables listVariables,
            final RealmDefinitions realmDefinitions,
            final EnvironmentVariables environmentVariables,
            final ShellSession shellSession) {
        return new SessionDeclarationWarning(
                questions -> new DeclarationDeletions(
                        new ResolveVariables(
                                listVariables, name -> Optional.ofNullable(environmentVariables.get(name)), questions),
                        realmDefinitions),
                shellSession);
    }

    @Bean
    public ConnectionCommands connectionCommands(
            final ConnectTarget connectTarget,
            final ShellSession shellSession,
            final EnvironmentVariables environmentVariables) {
        return new ConnectionCommands(connectTarget, shellSession, environmentVariables);
    }

    /**
     * The dry run and the import of the opened realm: the use case is built for each command, to ask its questions on
     * the console of that command; {@code KCC_VAR_<name>} is read from the environment.
     */
    @Bean
    public RealmImportCommands realmImportCommands(
            final ListVariables listVariables,
            final RealmDefinitions realmDefinitions,
            final EnvironmentVariables environmentVariables,
            final ShellSession shellSession) {
        return new RealmImportCommands(
                questions -> new ImportOpenedRealm(
                        new ResolveVariables(
                                listVariables, name -> Optional.ofNullable(environmentVariables.get(name)), questions),
                        realmDefinitions),
                shellSession);
    }

    /**
     * The parser of the commands, for the session and the scripts: its errors never show the value of a
     * {@code --var}.
     */
    @Bean
    public CommandParser commandParser(final CommandRegistry commandRegistry) {
        return new SafeCommandParser(new DefaultCommandParser(commandRegistry));
    }

    /** Replaces the console input of Spring Shell, whose prompt is fixed: it shows the realm and the target. */
    @Bean
    public ConsoleInputProvider consoleInputProvider(final ShellSession shellSession) {
        return new ShellInputProvider(new ShellPrompt(shellSession));
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
            final ObjectProvider<ShellRunner> interactiveShell,
            final ConnectionCommands connectionCommands) {
        return new ShellLauncher(
                properties,
                ruleBook,
                readRealms,
                shellSession,
                commandParser,
                commandRegistry,
                interactiveShell,
                new PrintWriter(System.out, true),
                connectionCommands);
    }
}
