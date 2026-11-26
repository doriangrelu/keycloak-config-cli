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
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Options of the shell (ADR-008).
 *
 * <p>{@code shell.root} is the folder holding one folder per realm, the working directory by default.
 * {@code shell.rules}, when given, is the file of the organisation rules, relative to the working directory.
 * {@code shell.command}, when given, runs that one command and exits, for a script; without it, an interactive session
 * starts. {@code shell.script}, when given, is a file of commands, one per line, run in one session; it cannot be combined
 * with {@code shell.command}. {@code shell.realm}, when given, is the folder of a realm opened at start.
 * {@code shell.target}, when given, is the name of a target of {@code kcc-targets.yaml} the shell connects to at start.
 */
@ConfigurationProperties(prefix = "shell", ignoreUnknownFields = false)
public class ShellProperties {

    private final Path root;
    private final Path rules;
    private final String command;
    private final String realm;
    private final Path script;
    private final String target;

    /** Options without a target to connect to at start. */
    ShellProperties(final Path root, final Path rules, final String command, final String realm, final Path script) {
        this(root, rules, command, realm, script, null);
    }

    @ConstructorBinding
    public ShellProperties(
            final Path root,
            final Path rules,
            final String command,
            final String realm,
            final Path script,
            final String target) {
        this.root = root == null ? Path.of(".") : root;
        this.rules = rules;
        this.command = command;
        this.realm = realm;
        this.script = script;
        this.target = target;
    }

    public Path getRoot() {
        return this.root;
    }

    public Optional<Path> getRules() {
        return Optional.ofNullable(this.rules);
    }

    public Optional<String> getCommand() {
        return Optional.ofNullable(this.command).filter(line -> !line.isBlank());
    }

    public Optional<String> getRealm() {
        return Optional.ofNullable(this.realm).map(String::strip).filter(name -> !name.isEmpty());
    }

    public Optional<Path> getScript() {
        return Optional.ofNullable(this.script).filter(path -> !path.toString().isBlank());
    }

    public Optional<String> getTarget() {
        return Optional.ofNullable(this.target).map(String::strip).filter(name -> !name.isEmpty());
    }
}
