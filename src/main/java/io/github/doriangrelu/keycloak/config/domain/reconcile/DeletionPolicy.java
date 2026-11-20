package io.github.doriangrelu.keycloak.config.domain.reconcile;

import java.util.List;

/**
 * Decides whether a resource missing from the import files may be deleted (ADR-007): unless a protection rule of the
 * files matches its name, by equality or by pattern, for every type or for its own.
 *
 * <p>The other guarantees are kept by each resource type: users are never deleted, nor the resources Keycloak creates
 * (default roles, built-in clients...).
 */
public record DeletionPolicy(List<ProtectionRule> protections) {

    public DeletionPolicy {
        protections = List.copyOf(protections);
    }

    public boolean allowsDeletionOf(final ResourceType type, final String name) {
        return this.protections.stream().noneMatch(rule -> rule.protects(type, name));
    }
}
