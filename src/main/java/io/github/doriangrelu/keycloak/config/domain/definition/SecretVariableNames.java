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

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The names of the variables that stand for the secrets of a realm: camel case, the separators removed, without the
 * braces of the placeholder.
 */
public final class SecretVariableNames {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]*}}");
    private static final Pattern SEPARATOR = Pattern.compile("[^A-Za-z0-9]+");

    private SecretVariableNames() {}

    /**
     * The secret of a client: the literal parts of its id followed by {@code Secret}, so {@code my-api} gives {@code
     * myApiSecret} and {@code {{env}}-api} gives {@code apiSecret}.
     */
    public static String client(final String clientId) {
        return camelCase(clientId, "client") + "Secret";
    }

    /** The secret of an identity provider: {@code google} gives {@code googleClientSecret}. */
    public static String identityProvider(final String alias) {
        return camelCase(alias, "identityProvider") + "ClientSecret";
    }

    /** The password of the SMTP server. */
    public static String smtpPassword() {
        return "smtpPassword";
    }

    /** The bind credential of a component: {@code corporate ldap} gives {@code corporateLdapBindCredential}. */
    public static String component(final String name) {
        return camelCase(name, "component") + "BindCredential";
    }

    /** The name, or the name followed by {@code 2}, {@code 3}... when it is taken already. */
    public static String unique(final String name, final Set<String> taken) {
        Objects.requireNonNull(taken, "taken");
        if (!taken.contains(name)) {
            return name;
        }
        int suffix = 2;
        while (taken.contains(name + suffix)) {
            suffix++;
        }
        return name + suffix;
    }

    private static String camelCase(final String text, final String fallback) {
        String literal =
                PLACEHOLDER.matcher(Objects.requireNonNull(text, "text")).replaceAll(" ");
        String[] parts = Arrays.stream(SEPARATOR.split(literal))
                .filter(part -> !part.isEmpty())
                .toArray(String[]::new);
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            name.append(i == 0 ? lowerFirst(parts[i]) : upperFirst(parts[i]));
        }
        if (name.isEmpty()) {
            name.append(fallback);
        } else if (Character.isDigit(name.charAt(0))) {
            name.insert(0, fallback);
        }
        return name.toString();
    }

    private static String lowerFirst(final String text) {
        return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }

    private static String upperFirst(final String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
