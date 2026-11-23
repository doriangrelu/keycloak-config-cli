package io.github.doriangrelu.keycloak.config.infrastructure.source;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The variables of a Mustache template, with default values written {@code {{name:default}}}.
 *
 * <p>A name holding a colon is looked up by the part before the colon; the part after it is the value when the variable
 * is not defined. A name without a colon and without a variable is not found: the template fails on it (ADR-007), rather
 * than rendering an empty value.
 */
final class MustacheContextWithDefaults extends AbstractMap<String, Object> {

    private static final char DEFAULT_VALUE_SEPARATOR = ':';

    private final Map<String, String> variables;

    MustacheContextWithDefaults(final Map<String, String> variables) {
        this.variables = Map.copyOf(Objects.requireNonNull(variables, "variables"));
    }

    @Override
    public Object get(final Object key) {
        String name = String.valueOf(key);
        String value = this.variables.get(name);
        if (value != null) {
            return value;
        }
        int separator = name.indexOf(DEFAULT_VALUE_SEPARATOR);
        if (separator < 0) {
            return null;
        }
        return this.variables.getOrDefault(name.substring(0, separator), name.substring(separator + 1));
    }

    @Override
    public boolean containsKey(final Object key) {
        String name = String.valueOf(key);
        return this.variables.containsKey(name) || name.indexOf(DEFAULT_VALUE_SEPARATOR) >= 0;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return this.variables.entrySet().stream()
                .map(entry -> new SimpleImmutableEntry<String, Object>(entry.getKey(), entry.getValue()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
