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

import java.util.List;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;

/**
 * Keeps at {@code INFO} at most the loggers that would show the value of a variable at {@code DEBUG} or {@code TRACE}:
 * the parser of Spring Shell and its command adapter log the command line, the import logs the rendered files and the
 * resources it compares. A more verbose level set by the user is lowered when the shell starts; a quieter one is kept.
 */
public final class ShellLogLevels {

    /** The loggers that would show a value. */
    static final List<String> PINNED = List.of(
            "org.springframework.shell.core.command.DefaultCommandParser",
            "org.springframework.shell.core.command.adapter.MethodInvokerCommandAdapter",
            "io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.RealmDocuments",
            "io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping.CloneUtil");

    private ShellLogLevels() {}

    /** Lowers the loggers that would show a value to {@code INFO}. */
    public static ShellLogLevels pin(final LoggingSystem logging) {
        for (String name : PINNED) {
            LoggerConfiguration configuration = logging.getLoggerConfiguration(name);
            LogLevel effective = configuration == null ? null : configuration.getEffectiveLevel();
            if (effective == null || effective.compareTo(LogLevel.INFO) < 0) {
                logging.setLogLevel(name, LogLevel.INFO);
            }
        }
        return new ShellLogLevels();
    }
}
