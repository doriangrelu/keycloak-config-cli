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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.util.Map;
import java.util.Objects;

/**
 * Renders the import files as Mustache templates (ADR-007), the only template engine of keycloak-config-cli.
 *
 * <ul>
 *   <li>{@code {{name}}} is the value of {@code import.mustache.variables.name}, and {@code {{name:default}}} falls back
 *       to {@code default}. A variable that is neither defined nor given a default fails the import: an empty secret
 *       or redirect URI is worse than an error.
 *   <li>Values are escaped as the content of a double-quoted string, which is the same in JSON and in YAML: a quote,
 *       a backslash or a line break neither breaks the file nor changes its meaning. HTML is not escaped.
 *   <li>{@code {{{name}}}} inserts the value as it is, to inject a number, a boolean or a whole JSON or YAML fragment.
 * </ul>
 */
public final class MustacheTemplates {

    private static final Mustache.Compiler COMPILER = Mustache.compiler().withEscaper(MustacheTemplates::escape);

    private final Map<String, String> variables;

    public MustacheTemplates(final Map<String, String> variables) {
        this.variables = Map.copyOf(Objects.requireNonNull(variables, "variables"));
    }

    /**
     * @param file name of the file, for the error messages
     * @throws InvalidImportException when the template is invalid or uses an undefined variable without default
     */
    public String render(final String file, final String content) {
        try {
            return COMPILER.compile(content).execute(new MustacheContextWithDefaults(this.variables));
        } catch (MustacheException.Context e) {
            throw new InvalidImportException(
                    "Undefined variable '%s' in file '%s' at line %d: set import.mustache.variables.%s, or give it a default value with {{%s:default}}"
                            .formatted(e.key, file, e.lineNo, e.key, e.key),
                    e);
        } catch (MustacheException e) {
            throw new InvalidImportException("Invalid template in file '%s': %s".formatted(file, e.getMessage()), e);
        }
    }

    /** Escapes a value as the content of a double-quoted JSON or YAML string. */
    static String escape(final String raw) {
        StringBuilder escaped = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append("\\u%04x".formatted((int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
