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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A value as written in a definition file: plain, a variable ({@code {{appUrl}}}, {@code {{appUrl:default}}}), or
 * a value composed with variables ({@code {{env}}-my-realm}). The value is kept as written; it is only rendered at
 * import (ADR-007, ADR-008).
 *
 * @param raw the value as written
 */
public record TemplatedValue(String raw) {

    /** A variable of a value, with its default value when it has one. */
    public record Variable(String name, Optional<String> defaultValue) {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    public TemplatedValue {
        Objects.requireNonNull(raw, "raw");
    }

    /** @throws InvalidTemplatedValueException when a placeholder is not valid */
    public static TemplatedValue of(final String raw) {
        TemplatedValue value = new TemplatedValue(raw);
        value.variables();
        return value;
    }

    public boolean isTemplated() {
        return this.raw.contains("{{");
    }

    /** Whether the value is a single placeholder: what it will be is unknown until the import. */
    public boolean isFullyVariable() {
        return PLACEHOLDER.matcher(this.raw).matches();
    }

    /**
     * @throws InvalidTemplatedValueException when a placeholder is not valid
     */
    public List<Variable> variables() {
        if (this.raw.contains("{{{")) {
            throw new InvalidTemplatedValueException(
                    this.raw, "a value inserted as it is ({{{...}}}) is not supported");
        }
        List<Variable> variables = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(this.raw);
        int end = 0;
        while (matcher.find()) {
            checkNoBraces(this.raw.substring(end, matcher.start()));
            variables.add(variableOf(matcher.group(1)));
            end = matcher.end();
        }
        checkNoBraces(this.raw.substring(end));
        return List.copyOf(variables);
    }

    private void checkNoBraces(final String text) {
        if (text.contains("{{") || text.contains("}}")) {
            throw new InvalidTemplatedValueException(this.raw, "a placeholder is not closed");
        }
    }

    private Variable variableOf(final String content) {
        int separator = content.indexOf(':');
        String name = separator < 0 ? content : content.substring(0, separator);
        if (!NAME.matcher(name).matches()) {
            throw new InvalidTemplatedValueException(this.raw, "'" + name + "' is not a valid variable name");
        }
        return new Variable(name, separator < 0 ? Optional.empty() : Optional.of(content.substring(separator + 1)));
    }
}
