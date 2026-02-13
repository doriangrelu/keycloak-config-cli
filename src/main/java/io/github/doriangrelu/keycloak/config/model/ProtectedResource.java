package io.github.doriangrelu.keycloak.config.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * @author Dorian GRELU
 * @since 02.2026
 */
public record ProtectedResource(
        Rule rule,
        String matcher
) {

    private static final Map<String, Pattern> lazyPattern = new ConcurrentHashMap<>();

    public enum Rule {
        PATTERN,
        EQUALS

    }

    public boolean doesMatch(final String value) {
        return switch (this.rule) {
            case EQUALS -> null != value && value.equals(this.matcher);
            case PATTERN -> null != value && this.matches(value);
        };
    }

    private boolean matches(final String value) {
        final Pattern pattern = lazyPattern.compute(this.matcher, (_, existsPattern) -> null == existsPattern ? Pattern.compile(this.matcher) : existsPattern);
        return pattern.matcher(value).matches();
    }


}
