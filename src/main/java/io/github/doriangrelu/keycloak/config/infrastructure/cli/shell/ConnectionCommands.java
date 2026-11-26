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
import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.MissingAnswerException;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;

/** Connects the shell to a target of kcc-targets.yaml, and lists the targets (ADR-010). */
public class ConnectionCommands {

    private final ConnectTarget connectTarget;
    private final ShellSession session;
    private final EnvironmentVariables environment;

    public ConnectionCommands(
            final ConnectTarget connectTarget, final ShellSession session, final EnvironmentVariables environment) {
        this.connectTarget = Objects.requireNonNull(connectTarget, "connectTarget");
        this.session = Objects.requireNonNull(session, "session");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @Command(
            name = "connect",
            group = "Connection",
            description = "Connect to a target of kcc-targets.yaml; the secret is read from KCC_<TARGET>_PASSWORD or "
                    + "KCC_<TARGET>_SECRET, or asked")
    public String connect(
            final CommandContext context,
            @Argument(index = 0, description = "the name of the target") final String target) {
        return connect(target, new ConsoleQuestions(context.inputReader(), context.outputWriter()));
    }

    /**
     * Connects to the target. The previous connection stays open until the new one is: a failure leaves the session as
     * it was.
     */
    String connect(final String name, final Questions questions) {
        Connection opened = this.connectTarget.connect(name, target -> secretOf(target, questions));
        this.session.connect(opened);
        return "Connected to %s (%s, Keycloak %s)"
                .formatted(opened.target().name(), opened.target().url(), opened.keycloakVersion());
    }

    @Command(name = "disconnect", group = "Connection", description = "Close the connection to the current target")
    public String disconnect() {
        return this.session
                .disconnect()
                .map(target -> "Disconnected from " + target.name())
                .orElse("Not connected: use 'connect <target>'");
    }

    @Command(
            name = {"target", "list"},
            group = "Connection",
            description = "List the targets of kcc-targets.yaml, and the one the shell is connected to")
    public String list() {
        List<Target> targets = this.connectTarget.targets();
        if (targets.isEmpty()) {
            return "No target in kcc-targets.yaml";
        }
        Optional<String> connected =
                this.session.connection().map(connection -> connection.target().name());
        return targets.stream()
                .map(target -> "%s  %s  %s%s"
                        .formatted(
                                target.name(),
                                target.url(),
                                target.grantType().optionName(),
                                connected.filter(target.name()::equals).isPresent() ? "  (connected)" : ""))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /** The secret of the target: its environment variable when set, else asked without echo. */
    private String secretOf(final Target target, final Questions questions) {
        String variable = target.secretVariable();
        String value = this.environment.get(variable);
        if (value != null && !value.isBlank()) {
            return value;
        }
        String question = (target.grantType() == GrantType.PASSWORD
                        ? "Password of '" + target.user().orElse("") + "'"
                        : "Secret of client '" + target.clientId() + "'")
                + " (or " + variable + ")";
        try {
            return questions.askSecret(question);
        } catch (MissingAnswerException noAnswer) {
            throw new MissingAnswerException(question, "set " + variable + " in the environment");
        }
    }
}
