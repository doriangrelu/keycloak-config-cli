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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.DefaultCommandParser;
import org.springframework.shell.core.command.ParsedInput;

class SafeCommandParserTest {

    private static final String SECRET = "ab'cd-s3cr3t-marker";

    private final SafeCommandParser parser = new SafeCommandParser(new DefaultCommandParser(new CommandRegistry()));

    @Test
    void aParseFailureHidesTheValuesOfVar() {
        Throwable failure = catchThrowable(() -> this.parser.parse("realm dry-run --var shopSecret=" + SECRET));

        assertThat(failure)
                .isInstanceOf(UnparsableCommandException.class)
                .hasMessage("Unbalanced quote (') in input: realm dry-run --var shopSecret=***")
                .hasNoCause();
        assertThat(failure.getSuppressed()).isEmpty();
    }

    @Test
    void anUnclosedDoubleQuoteHidesTheValueToo() {
        Throwable failure = catchThrowable(() -> this.parser.parse("realm import --var \"title=s3cr3t-marker --apply"));

        assertThat(failure).hasMessageNotContaining("s3cr3t-marker").hasNoCause();
    }

    @Test
    void aValidLineIsParsedByTheParserOfSpringShell() {
        ParsedInput parsed = this.parser.parse("connect local");

        assertThat(parsed.commandName()).isEqualTo("connect");
    }
}
