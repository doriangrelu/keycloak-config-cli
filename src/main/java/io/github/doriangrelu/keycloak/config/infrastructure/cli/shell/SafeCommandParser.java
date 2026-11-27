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

import java.util.Objects;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.ParsedInput;

/**
 * The parser of Spring Shell, whose errors never show the value of a {@code --var}: a line it cannot parse (an
 * unbalanced quote, for example) is quoted in its message, and the session or the script prints that message.
 */
public final class SafeCommandParser implements CommandParser {

    private final CommandParser parser;

    public SafeCommandParser(final CommandParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    /**
     * @throws UnparsableCommandException when the line cannot be parsed, its message holding the line with the values of
     *     {@code --var} hidden; the error of the parser is not kept, since its message holds them
     */
    @Override
    public ParsedInput parse(final String input) {
        try {
            return this.parser.parse(input);
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null ? "Cannot parse the command" : failure.getMessage();
            throw new UnparsableCommandException(
                    input == null ? message : message.replace(input, ShellLauncher.shown(input)));
        }
    }
}
