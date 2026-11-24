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

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Options of the shell (ADR-008).
 *
 * <p>{@code shell.root} is the folder holding one folder per realm, the working directory by default.
 * {@code shell.command}, when given, runs that one command and exits, for a script; without it, an interactive session
 * starts.
 */
@ConfigurationProperties(prefix = "shell", ignoreUnknownFields = false)
public class ShellProperties {

    private final Path root;
    private final String command;

    public ShellProperties(final Path root, final String command) {
        this.root = root == null ? Path.of(".") : root;
        this.command = command;
    }

    public Path getRoot() {
        return this.root;
    }

    public Optional<String> getCommand() {
        return Optional.ofNullable(this.command).filter(line -> !line.isBlank());
    }
}
