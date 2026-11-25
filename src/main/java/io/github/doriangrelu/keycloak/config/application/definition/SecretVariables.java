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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** The variable proposed for the secret of a client. */
final class SecretVariables {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]*}}");
    private static final Pattern SEPARATOR = Pattern.compile("[^A-Za-z0-9]+");

    private SecretVariables() {}

    /**
     * The literal parts of the id, split on every character that is not alphanumeric, in camel case and followed by
     * {@code Secret}: {@code my-api} gives {@code {{myApiSecret}}}, {@code {{env}}-api} gives {@code {{apiSecret}}}.
     */
    static TemplatedValue proposedFor(final TemplatedValue clientId) {
        String literal = PLACEHOLDER.matcher(clientId.raw()).replaceAll(" ");
        String[] parts = Arrays.stream(SEPARATOR.split(literal))
                .filter(part -> !part.isEmpty())
                .toArray(String[]::new);
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            name.append(i == 0 ? lowerFirst(parts[i]) : upperFirst(parts[i]));
        }
        if (name.isEmpty()) {
            name.append("client");
        } else if (Character.isDigit(name.charAt(0))) {
            name.insert(0, "client");
        }
        return TemplatedValue.of("{{" + name + "Secret}}");
    }

    private static String lowerFirst(final String text) {
        return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }

    private static String upperFirst(final String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
