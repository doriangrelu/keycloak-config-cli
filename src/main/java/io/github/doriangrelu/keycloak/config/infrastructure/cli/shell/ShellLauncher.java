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

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCode;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.FailureReporter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
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
    private final ReadRealms readRealms;
    private final ShellSession session;
    private final CommandParser parser;
    private final CommandRegistry registry;
    private final ObjectProvider<ShellRunner> interactiveShell;
    private final PrintWriter output;
    private int exitCode = ExitCode.SUCCESS.code();

    public ShellLauncher(
            final ShellProperties properties,
            final RuleBook ruleBook,
            final ReadRealms readRealms,
            final ShellSession session,
            final CommandParser parser,
            final CommandRegistry registry,
            final ObjectProvider<ShellRunner> interactiveShell,
            final PrintWriter output) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.ruleBook = Objects.requireNonNull(ruleBook, "ruleBook");
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.session = Objects.requireNonNull(session, "session");
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
            Optional<Path> script = this.properties.getScript();
            if (command.isPresent() && script.isPresent()) {
                this.exitCode = fail("shell.command and shell.script cannot be used together");
                return;
            }
            openRealm();
            if (command.isPresent()) {
                this.exitCode = runOnce(command.get());
            } else if (script.isPresent()) {
                this.exitCode = runScript(script.get());
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

    /** The realm of {@code shell.realm} is opened before the first command; an unknown one stops the launch. */
    private void openRealm() {
        Optional<String> realm = this.properties.getRealm();
        if (realm.isPresent()) {
            try {
                this.readRealms.open(realm.get());
            } catch (ApplicationException e) {
                this.output.println(e.getMessage());
                this.output.flush();
                throw e;
            }
            this.session.open(realm.get());
        }
    }

    /**
     * Runs the commands of the file in order, in one session: the first one that fails stops the script with its exit
     * code.
     */
    private int runScript(final Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return fail("Script not found: " + file);
        } catch (IOException e) {
            return fail("Cannot read " + file + ": " + messageOf(e));
        }
        boolean any = false;
        for (int index = 0; index < lines.size(); index++) {
            String command = lines.get(index).strip();
            if (command.isEmpty() || command.startsWith("#")) {
                continue;
            }
            any = true;
            this.output.println("> " + command);
            int code = runSafely(command);
            if (code != ExitCode.SUCCESS.code()) {
                this.output.println("Stopped at line " + (index + 1) + ": " + command);
                this.output.flush();
                return code;
            }
        }
        if (!any) {
            this.output.println("No command in " + file);
            this.output.flush();
        }
        return ExitCode.SUCCESS.code();
    }

    private int fail(final String message) {
        this.output.println(message);
        this.output.flush();
        return ExitCode.INVALID_CONFIGURATION.code();
    }

    /** A command that cannot even be parsed fails like any other, so that the script reports where it stopped. */
    private int runSafely(final String line) {
        try {
            return runOnce(line);
        } catch (Exception e) {
            this.output.println(messageOf(e));
            return ExitCodeResolver.resolve(e).code();
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
            this.output.println(messageOf(cause));
            return ExitCodeResolver.resolve(cause).code();
        } finally {
            this.output.flush();
        }
    }

    /** What to tell the user: the first message of the chain, a wrapper such as an invocation error has none. */
    private static String messageOf(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                return current.getMessage();
            }
        }
        return failure.toString();
    }

    @Override
    public int getExitCode() {
        return this.exitCode;
    }
}
