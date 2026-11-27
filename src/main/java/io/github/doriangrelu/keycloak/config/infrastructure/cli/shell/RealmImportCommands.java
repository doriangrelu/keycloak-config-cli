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

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.ImportOpenedRealm;
import io.github.doriangrelu.keycloak.config.application.definition.PreparedImport;
import io.github.doriangrelu.keycloak.config.application.definition.ResolveVariables;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.DryRunReport;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandOption;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/**
 * The dry run and the import of the opened realm on the connected target (ADR-010). The values of the variables come
 * from {@code --var}, the session, {@code KCC_VAR_<name>}, the default value of the placeholder, or a question; they
 * are never shown.
 */
public class RealmImportCommands {

    private static final String VAR = "var";

    private final Function<Questions, ImportOpenedRealm> importOpenedRealm;
    private final ShellSession session;

    /** @param importOpenedRealm the use case, asking its questions with the given ones */
    public RealmImportCommands(
            final Function<Questions, ImportOpenedRealm> importOpenedRealm, final ShellSession session) {
        this.importOpenedRealm = Objects.requireNonNull(importOpenedRealm, "importOpenedRealm");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Command(
            name = {"realm", "dry-run"},
            group = "Realms",
            description = "Show what importing the opened realm would change on the connected target, writing nothing;"
                    + " in a script, ends with code 10 when changes are pending")
    public String dryRun(
            final CommandContext context,
            @Option(
                            longName = VAR,
                            description = "the value of a variable, name=value; repeat it for each variable",
                            required = false)
                    final String var) {
        Map<String, String> given = ResolveVariables.parseVars(varsOf(context));
        String folder = this.session.requireOpenedRealm();
        Connection connection = this.session.requireConnection();
        PrintWriter writer = context.outputWriter();
        writer.println("Dry run of '%s' on %s".formatted(folder, where(connection.target())));
        writer.flush();
        ImportReport plan =
                importer(context.inputReader(), writer).dryRun(folder, connection, given, this.session.variables());
        DryRunReport report = DryRunReport.of(plan);
        if (report.pendingChanges() > 0) {
            this.session.notePendingChanges();
        }
        return report.toText().stripTrailing();
    }

    @Command(
            name = {"realm", "import"},
            group = "Realms",
            description = "Import the opened realm on the connected target: the changes are shown, then applied once"
                    + " confirmed, or at once with --apply")
    public String importRealm(
            final CommandContext context,
            @Option(
                            longName = VAR,
                            description = "the value of a variable, name=value; repeat it for each variable",
                            required = false)
                    final String var,
            @Option(
                            longName = "apply",
                            description = "apply the changes without asking",
                            required = false,
                            defaultValue = "false")
                    final boolean apply) {
        Map<String, String> given = ResolveVariables.parseVars(varsOf(context));
        String folder = this.session.requireOpenedRealm();
        Connection connection = this.session.requireConnection();
        Target target = connection.target();
        PrintWriter writer = context.outputWriter();
        writer.println("Import of '%s' on %s".formatted(folder, where(target)));
        writer.flush();
        ImportOpenedRealm importer = importer(context.inputReader(), writer);
        PreparedImport prepared = importer.prepare(folder, connection, given, this.session.variables());
        ImportReport plan = prepared.report();
        DryRunReport planned = DryRunReport.of(plan);
        String changes = planned.changesText().stripTrailing();
        if (!changes.isEmpty()) {
            writer.println(changes);
            writer.flush();
        }
        int pending = planned.pendingChanges();
        if (pending == 0) {
            return "Nothing to import";
        }
        if (!apply) {
            WatchedReader reader = new WatchedReader(context.inputReader());
            boolean confirmed = new ConsoleQuestions(reader, writer)
                    .confirm("Apply these %d change(s) to %s?".formatted(pending, target.name()), false);
            if (!confirmed) {
                return reader.unanswered ? "Nothing written: run again with --apply" : "Nothing written";
            }
        }
        // the files of the plan the user saw, not a second reading of the folder
        ImportReport applied = importer.apply(prepared);
        Map<String, ?> realms = applied.plans().isEmpty() ? plan.plans() : applied.plans();
        return "Realm '%s' imported on %s: %d change(s) applied"
                .formatted(
                        String.join(", ", realms.keySet()),
                        target.name(),
                        DryRunReport.of(applied.plans()).pendingChanges());
    }

    private ImportOpenedRealm importer(final InputReader reader, final PrintWriter writer) {
        return this.importOpenedRealm.apply(new ConsoleQuestions(reader, writer));
    }

    /** Every {@code --var} of the command: Spring Shell gives only the first one to the parameter. */
    private static List<String> varsOf(final CommandContext context) {
        return context.parsedInput().options().stream()
                .filter(option -> VAR.equals(option.longName()))
                .map(CommandOption::value)
                .toList();
    }

    private static String where(final Target target) {
        return "%s (%s)".formatted(target.name(), target.url());
    }

    /** Tells a question left unanswered, as in a script, from a refusal. */
    private static final class WatchedReader implements InputReader {

        private final InputReader reader;
        private boolean unanswered;

        WatchedReader(final InputReader reader) {
            this.reader = reader;
        }

        @Override
        public String readInput() throws Exception {
            return readInput("");
        }

        @Override
        public String readInput(final String prompt) throws Exception {
            String answer;
            try {
                answer = this.reader.readInput(prompt);
            } catch (Exception e) {
                this.unanswered = true;
                throw e;
            }
            if (answer == null) {
                this.unanswered = true;
            }
            return answer;
        }

        @Override
        public char[] readPassword(final String prompt) throws Exception {
            return this.reader.readPassword(prompt);
        }
    }
}
