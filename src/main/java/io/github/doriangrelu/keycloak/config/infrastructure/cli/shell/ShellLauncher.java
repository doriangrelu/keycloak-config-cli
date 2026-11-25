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

import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCode;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.FailureReporter;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandExecutionException;
import org.springframework.shell.core.command.CommandExecutor;
import org.springframework.shell.core.command.CommandNotFoundException;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ExitStatus;
import org.springframework.shell.core.command.ParsedInput;

/**
 * Runs the shell (ADR-008): the command given by {@code shell.command}, once, for a script; otherwise an interactive
 * session. Registered as {@code springShellApplicationRunner}, so that Spring Shell does not start its own.
 */
public class ShellLauncher implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ShellLauncher.class);

    /** Answers nothing: in a script, a command that asks a question fails instead of waiting. */
    private static final InputReader NO_ANSWER = new InputReader() {
        @Override
        public String readInput() {
            return null;
        }

        @Override
        public String readInput(final String prompt) {
            return null;
        }
    };

    private final ShellProperties properties;
    private final RuleBook ruleBook;
    private final CommandParser parser;
    private final CommandRegistry registry;
    private final ObjectProvider<ShellRunner> interactiveShell;
    private final PrintWriter output;
    private int exitCode = ExitCode.SUCCESS.code();

    public ShellLauncher(
            final ShellProperties properties,
            final RuleBook ruleBook,
            final CommandParser parser,
            final CommandRegistry registry,
            final ObjectProvider<ShellRunner> interactiveShell,
            final PrintWriter output) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.ruleBook = Objects.requireNonNull(ruleBook, "ruleBook");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.interactiveShell = Objects.requireNonNull(interactiveShell, "interactiveShell");
        this.output = Objects.requireNonNull(output, "output");
    }

    @Override
    public void run(final ApplicationArguments arguments) {
        try {
            loadRules();
            Optional<String> command = this.properties.getCommand();
            if (command.isPresent()) {
                this.exitCode = runOnce(command.get());
            } else {
                this.interactiveShell.getObject().run(new String[0]);
            }
        } catch (Exception e) {
            this.exitCode = FailureReporter.report(logger, "shell", e).code();
        }
    }

    /** An invalid rules file stops the shell before the first command, with the reason. */
    private void loadRules() {
        try {
            this.ruleBook.rules();
        } catch (InvalidRulesException e) {
            this.output.println(e.getMessage());
            this.output.flush();
            throw e;
        }
    }

    private int runOnce(final String line) throws Exception {
        ParsedInput parsed = this.parser.parse(line);
        try {
            ExitStatus status = new CommandExecutor(this.registry)
                    .execute(new CommandContext(parsed, this.registry, this.output, NO_ANSWER));
            return status.code() == 0 ? ExitCode.SUCCESS.code() : ExitCode.INVALID_CONFIGURATION.code();
        } catch (CommandNotFoundException e) {
            this.output.println(e.getMessage());
            return ExitCode.INVALID_CONFIGURATION.code();
        } catch (CommandExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            this.output.println(cause.getMessage());
            return ExitCodeResolver.resolve(cause).code();
        } finally {
            this.output.flush();
        }
    }

    @Override
    public int getExitCode() {
        return this.exitCode;
    }
}
