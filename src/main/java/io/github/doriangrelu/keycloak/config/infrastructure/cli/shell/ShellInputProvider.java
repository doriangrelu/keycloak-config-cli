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

import java.io.Console;
import java.util.Objects;
import org.springframework.shell.core.ConsoleInputProvider;

/**
 * Reads the commands of an interactive session on the system console. Unlike its parent, whose prompt is fixed, it
 * shows the prompt of the moment: the opened realm and the connected target change between two commands.
 */
public final class ShellInputProvider extends ConsoleInputProvider {

    private final ShellPrompt prompt;

    /** Reads on the system console. */
    public ShellInputProvider(final ShellPrompt prompt) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
    }

    /** Reads on this console. */
    public ShellInputProvider(final ShellPrompt prompt, final Console console) {
        super(console);
        this.prompt = Objects.requireNonNull(prompt, "prompt");
    }

    @Override
    public String readInput() {
        // the prompt holds names typed by the user, which may hold a percent sign: it is an argument, never the format
        return getConsole().readLine("%s", this.prompt.text() + " ");
    }
}
