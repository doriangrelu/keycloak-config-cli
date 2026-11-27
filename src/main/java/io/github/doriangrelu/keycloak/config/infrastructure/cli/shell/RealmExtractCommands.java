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
import io.github.doriangrelu.keycloak.config.application.definition.ExtractRealm;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/**
 * Extracts a realm of the connected target into a new realm folder, then opens it (ADR-010). The current secrets of
 * the confidential clients are kept in the memory of the session, never written nor shown.
 */
public class RealmExtractCommands {

    private final ExtractRealm extractRealm;
    private final RealmCommands realmCommands;
    private final ShellSession session;

    public RealmExtractCommands(
            final ExtractRealm extractRealm, final RealmCommands realmCommands, final ShellSession session) {
        this.extractRealm = Objects.requireNonNull(extractRealm, "extractRealm");
        this.realmCommands = Objects.requireNonNull(realmCommands, "realmCommands");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Command(
            name = {"realm", "extract"},
            group = "Realms",
            description = "Write a realm of the connected target to a new realm folder, its secrets as variables,"
                    + " then open it")
    public String extract(
            @Argument(index = 0, description = "the name of the realm on the target") final String realm,
            @Option(longName = "folder", description = "the folder to write, the realm by default", required = false)
                    final String folderOption,
            @Option(longName = "format", description = "yaml (default) or json", required = false)
                    final String formatOption,
            @Option(
                            longName = "with-users",
                            description = "extract the users too, without their credentials",
                            required = false,
                            defaultValue = "false")
                    final boolean withUsers) {
        Connection connection = this.session.requireConnection();
        Optional<String> folder = given(folderOption);
        DefinitionFormat format =
                given(formatOption).map(RealmCommands::formatOf).orElse(DefinitionFormat.YAML);
        ExtractRealm.Extraction extraction = this.extractRealm.extract(realm, folder, format, withUsers, connection);
        StringJoiner lines = new StringJoiner(System.lineSeparator());
        lines.add("Realm '%s' extracted to %s/: %s"
                .formatted(realm, extraction.folder(), String.join(", ", extraction.files())));
        if (!extraction.variablesToGive().isEmpty()) {
            lines.add("Variables to give at import: " + String.join(", ", extraction.variablesToGive()));
        }
        if (extraction.clientSecretsKept()) {
            lines.add("(client secrets read from %s and kept for this session)"
                    .formatted(connection.target().name()));
        }
        extraction.warnings().forEach(lines::add);
        if (extraction.skippedUsers() > 0) {
            lines.add(
                    "%d users from a directory or service accounts not extracted".formatted(extraction.skippedUsers()));
        }
        // opening forgets the values of the variables: the secrets are kept after
        lines.add(this.realmCommands.open(extraction.folder()));
        extraction.clientSecrets().forEach(this.session.variables()::remember);
        return lines.toString();
    }

    private static Optional<String> given(final String option) {
        return Optional.ofNullable(option).map(String::strip).filter(value -> !value.isEmpty());
    }
}
