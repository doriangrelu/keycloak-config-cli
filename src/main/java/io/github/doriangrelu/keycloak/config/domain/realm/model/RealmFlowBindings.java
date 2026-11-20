package io.github.doriangrelu.keycloak.config.domain.realm.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The flows a realm uses, by binding ({@code browserFlow}, {@code directGrantFlow}...), given by alias. Bound once the
 * flows of the import exist. A binding the import omits keeps its flow.
 */
public record RealmFlowBindings(Map<String, String> flows) implements Resource<String> {

    /** Key of the only flow bindings of a realm. */
    public static final String KEY = "flow-bindings";

    public RealmFlowBindings {
        Map<String, String> copy = new LinkedHashMap<>();
        if (flows != null) {
            flows.forEach((binding, alias) -> {
                if (alias != null) {
                    copy.put(binding, alias);
                }
            });
        }
        flows = Collections.unmodifiableMap(copy);
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String protectedName() {
        return KEY;
    }

    /** The bindings that differ from the ones of {@code reference} (normalization). */
    public RealmFlowBindings withoutValuesOf(final RealmFlowBindings reference) {
        Map<String, String> differing = new LinkedHashMap<>(this.flows);
        differing.entrySet().removeIf(binding -> binding.getValue().equals(reference.flows.get(binding.getKey())));
        return new RealmFlowBindings(differing);
    }

    public boolean isEmpty() {
        return this.flows.isEmpty();
    }

    /** These bindings once {@code desired} is applied on them. */
    public RealmFlowBindings mergedWith(final RealmFlowBindings desired) {
        Map<String, String> merged = new LinkedHashMap<>(this.flows);
        merged.putAll(desired.flows);
        return new RealmFlowBindings(merged);
    }
}
