package io.github.doriangrelu.keycloak.config.domain.reconcile;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Protects resources from deletion, declared by {@code protectedResources} in an import file (ADR-007). A protected
 * resource missing from the files is never deleted.
 *
 * <p>The rule compares the name of a resource ({@link Resource#protectedName()}) by equality or with a regular
 * expression matching the whole name, compiled once. Without a type, it protects the resources of every type; with a
 * type, only those of that type.
 */
public final class ProtectionRule {

    public enum Matching {
        EQUALS,
        PATTERN
    }

    private final Matching matching;
    private final String matcher;
    private final ResourceType type;
    private final Pattern pattern;

    /** A rule protecting the resources of every type. */
    public ProtectionRule(final Matching matching, final String matcher) {
        this(matching, matcher, null);
    }

    /**
     * @param matching how {@code matcher} is compared to the name
     * @param matcher the exact name, or a regular expression matching the whole name
     * @param type the only resource type the rule protects; {@code null} for every type
     * @throws InvalidProtectionRuleException when the matcher is missing or is an invalid regular expression
     */
    public ProtectionRule(final Matching matching, final String matcher, final ResourceType type) {
        if (matcher == null) {
            throw new InvalidProtectionRuleException("A protected resource has no matcher");
        }
        this.matching = Objects.requireNonNullElse(matching, Matching.EQUALS);
        this.matcher = matcher;
        this.type = type;
        this.pattern = this.matching == Matching.PATTERN ? compile(matcher) : null;
    }

    private static Pattern compile(final String matcher) {
        try {
            return Pattern.compile(matcher);
        } catch (PatternSyntaxException e) {
            throw new InvalidProtectionRuleException(
                    "Invalid regular expression '%s' in protectedResources: %s".formatted(matcher, e.getDescription()),
                    e);
        }
    }

    public Matching matching() {
        return this.matching;
    }

    public String matcher() {
        return this.matcher;
    }

    public Optional<ResourceType> type() {
        return Optional.ofNullable(this.type);
    }

    /** Whether this rule protects the resource of this type and name. */
    public boolean protects(final ResourceType resourceType, final String name) {
        if (name == null || (this.type != null && this.type != resourceType)) {
            return false;
        }
        return switch (this.matching) {
            case EQUALS -> this.matcher.equals(name);
            case PATTERN -> this.pattern.matcher(name).matches();
        };
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof ProtectionRule rule
                && this.matching == rule.matching
                && this.matcher.equals(rule.matcher)
                && this.type == rule.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.matching, this.matcher, this.type);
    }

    @Override
    public String toString() {
        return "ProtectionRule[" + this.matching + " " + this.matcher + (this.type == null ? "" : " " + this.type)
                + "]";
    }
}
