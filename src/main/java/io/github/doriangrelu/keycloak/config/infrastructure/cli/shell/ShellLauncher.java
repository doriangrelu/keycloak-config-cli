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
import org.springframework.shell.core.InteractiveShellRunner;
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

        @Override
        public char[] readPassword(final String prompt) {
            return null;
        }
    };

    private static final String VAR_OPTION = "--var";

    private final ShellProperties properties;
    private final RuleBook ruleBook;
    private final ReadRealms readRealms;
    private final ShellSession session;
    private final CommandParser parser;
    private final CommandRegistry registry;
    private final ObjectProvider<ShellRunner> interactiveShell;
    private final PrintWriter output;
    private final ConnectionCommands connections;
    private int exitCode = ExitCode.SUCCESS.code();

    public ShellLauncher(
            final ShellProperties properties,
            final RuleBook ruleBook,
            final ReadRealms readRealms,
            final ShellSession session,
            final CommandParser parser,
            final CommandRegistry registry,
            final ObjectProvider<ShellRunner> interactiveShell,
            final PrintWriter output,
            final ConnectionCommands connections) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.ruleBook = Objects.requireNonNull(ruleBook, "ruleBook");
        this.readRealms = Objects.requireNonNull(readRealms, "readRealms");
        this.session = Objects.requireNonNull(session, "session");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.interactiveShell = Objects.requireNonNull(interactiveShell, "interactiveShell");
        this.output = Objects.requireNonNull(output, "output");
        this.connections = Objects.requireNonNull(connections, "connections");
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
            ShellRunner interactive = command.isEmpty() && script.isEmpty() ? this.interactiveShell.getObject() : null;
            connectAtStart(interactive);
            if (command.isPresent()) {
                this.exitCode = runSafely(command.get());
            } else if (script.isPresent()) {
                this.exitCode = runScript(script.get());
            } else {
                interactive.run(new String[0]);
            }
        } catch (Exception e) {
            this.exitCode = FailureReporter.report(logger, "shell", e).code();
        } finally {
            this.session.close();
        }
    }

    /**
     * The target of {@code shell.target} is connected before the first command; a failure stops the launch with the
     * code of its cause. In a session the secret is asked on the console; otherwise it comes from the environment.
     */
    private void connectAtStart(final ShellRunner interactive) {
        Optional<String> target = this.properties.getTarget();
        if (target.isEmpty()) {
            return;
        }
        InputReader reader = readerOf(interactive);
        try {
            this.output.println(this.connections.connect(target.get(), new ConsoleQuestions(reader, this.output)));
            this.output.flush();
        } catch (ApplicationException e) {
            this.output.println(e.getMessage());
            this.output.flush();
            throw e;
        }
    }

    /**
     * The reader of the session, which asks on its console. A runner that cannot ask (the non-interactive one, or none:
     * a script) answers nothing, and the secret then has to come from the environment: this is said, not hidden.
     */
    private static InputReader readerOf(final ShellRunner interactive) {
        if (interactive instanceof InteractiveShellRunner runner) {
            return runner.getReader();
        }
        if (interactive != null) {
            logger.warn(
                    "The shell runner {} cannot ask a question: the secret of shell.target must be in the environment",
                    interactive.getClass().getSimpleName());
        }
        return NO_ANSWER;
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
     * code. A dry run that finds changes (code 10) does not stop it: the script then ends with 10 when nothing failed.
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
        boolean pendingChanges = false;
        for (int index = 0; index < lines.size(); index++) {
            String command = lines.get(index).strip();
            if (command.isEmpty() || command.startsWith("#")) {
                continue;
            }
            any = true;
            this.output.println("> " + shown(command));
            int code = runSafely(command);
            if (code == ExitCode.PENDING_CHANGES.code()) {
                pendingChanges = true;
                continue;
            }
            if (code != ExitCode.SUCCESS.code()) {
                this.output.println("Stopped at line " + (index + 1) + ": " + shown(command));
                this.output.flush();
                return code;
            }
        }
        if (!any) {
            this.output.println("No command in " + file);
            this.output.flush();
        }
        return pendingChanges ? ExitCode.PENDING_CHANGES.code() : ExitCode.SUCCESS.code();
    }

    /**
     * The command as the script shows it: the value of each {@code --var name=value} option is hidden, quoted or not,
     * as the parser of Spring Shell reads it.
     */
    static String shown(final String command) {
        StringBuilder shown = new StringBuilder();
        int index = 0;
        int option;
        while ((option = command.indexOf(VAR_OPTION, index)) >= 0) {
            int name = skipSeparators(command, option + VAR_OPTION.length());
            if (name < 0) {
                shown.append(command, index, option + VAR_OPTION.length());
                index = option + VAR_OPTION.length();
                continue;
            }
            int equal = name;
            while (equal < command.length()
                    && command.charAt(equal) != '='
                    && !Character.isWhitespace(command.charAt(equal))) {
                equal++;
            }
            if (equal == command.length() || command.charAt(equal) != '=') {
                if (name == command.length()) {
                    // no argument at all
                    shown.append(command, index, name);
                    index = name;
                    continue;
                }
                // no "=": nothing tells the name from the value, the whole argument is hidden
                shown.append(command, index, name).append("***");
                index = endOfWord(command, name, (char) 0);
                continue;
            }
            char nameQuote = isQuote(command.charAt(name)) ? command.charAt(name) : 0;
            int end = endOfWord(command, equal + 1, nameQuote);
            shown.append(command, index, equal + 1).append("***");
            if (nameQuote != 0) {
                shown.append(nameQuote);
            }
            index = end;
        }
        return shown.append(command.substring(index)).toString();
    }

    /** Where the value of the option starts: after {@code =}, or after the blanks; -1 when it is another option. */
    private static int skipSeparators(final String command, final int from) {
        if (from < command.length() && command.charAt(from) == '=') {
            return from + 1;
        }
        int index = from;
        while (index < command.length() && Character.isWhitespace(command.charAt(index))) {
            index++;
        }
        return index == from && index < command.length() ? -1 : index;
    }

    /** Where the word ends, as the parser of Spring Shell splits words: at a blank outside quotes. */
    private static int endOfWord(final String command, final int from, final char openedQuote) {
        char opened = openedQuote;
        int index = from;
        while (index < command.length()) {
            char current = command.charAt(index);
            if (current == '\\' && index + 1 < command.length()) {
                index++;
            } else if (opened != 0) {
                if (current == opened) {
                    opened = 0;
                }
            } else if (isQuote(current)) {
                opened = current;
            } else if (Character.isWhitespace(current)) {
                break;
            }
            index++;
        }
        return index;
    }

    private static boolean isQuote(final char character) {
        return character == '"' || character == '\'';
    }

    private int fail(final String message) {
        this.output.println(message);
        this.output.flush();
        return ExitCode.INVALID_CONFIGURATION.code();
    }

    /**
     * A command that cannot even be parsed fails like any other, with its message (its {@code --var} values hidden by
     * the parser): a script reports where it stopped, a single command says why.
     */
    private int runSafely(final String line) {
        try {
            return runOnce(line);
        } catch (Exception e) {
            this.output.println(messageOf(e));
            return ExitCodeResolver.resolve(e).code();
        }
    }

    /**
     * Runs one command: {@code 0} when it succeeds, or {@code 10} when it is a dry run that found changes, which the
     * command tells the session (Spring Shell gives no other code to a command that succeeds, and an interactive
     * session would print any other {@link ExitStatus} as an error).
     */
    private int runOnce(final String line) throws Exception {
        ParsedInput parsed = this.parser.parse(line);
        this.session.takePendingChanges();
        try {
            ExitStatus status = new CommandExecutor(this.registry)
                    .execute(new CommandContext(parsed, this.registry, this.output, NO_ANSWER));
            if (status.code() != 0) {
                return ExitCode.INVALID_CONFIGURATION.code();
            }
            return this.session.takePendingChanges() ? ExitCode.PENDING_CHANGES.code() : ExitCode.SUCCESS.code();
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
