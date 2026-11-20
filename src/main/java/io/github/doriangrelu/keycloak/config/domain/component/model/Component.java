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

package io.github.doriangrelu.keycloak.config.domain.component.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A component of a realm, such as a key provider or a user storage, or a sub-component of another component, such as
 * an LDAP mapper. Identified by its parent, its provider type, its sub-type and its name.
 *
 * <p>The {@code id} is the one Keycloak gave the component, or the one a desired component asks for when it is
 * created: users of a user storage refer to it. It never changes once the component exists.
 *
 * <p>In a desired component, a {@code null} provider id is not specified by the import file and keeps its value. The
 * declared {@code config} entries replace the current ones; undeclared entries keep their value.
 */
public record Component(Key key, String id, String providerId, Map<String, List<String>> config)
        implements Resource<Component.Key> {

    /** Provider types of the components Keycloak creates and depends on. */
    static final Set<String> TECHNICAL_PROVIDER_TYPES = Set.of(
            "org.keycloak.keys.KeyProvider",
            "org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy",
            "org.keycloak.userprofile.UserProfileProvider");

    /**
     * Identity of a component.
     *
     * @param parent key of the parent component, {@code null} for a component of the realm
     * @param subType sub-type, {@code null} for most provider types
     */
    public record Key(Key parent, String providerType, String subType, String name) {

        public Key {
            Objects.requireNonNull(providerType, "providerType");
            Objects.requireNonNull(name, "name");
        }

        public boolean isTopLevel() {
            return this.parent == null;
        }

        /** Path of the component from the realm: used as the scope of its sub-components. */
        public String path() {
            String own = this.providerType + ":" + (this.subType == null ? "" : this.subType) + ":" + this.name;
            return this.parent == null ? own : this.parent.path() + " > " + own;
        }

        @Override
        public String toString() {
            return path();
        }
    }

    public Component {
        Objects.requireNonNull(key, "key");
        config = config == null ? Map.of() : copyOf(config);
    }

    @Override
    public String stateName() {
        return this.key.name();
    }

    /** This component once {@code desired} is applied on it. */
    /**
     * Whether this component belongs to Keycloak, never deleted (ADR-007): the keys of the realm, the client
     * registration policies and the user profile. Deleting a key provider would invalidate every token of the realm.
     */
    public boolean isOwnedByKeycloak() {
        return this.key.isTopLevel() && TECHNICAL_PROVIDER_TYPES.contains(this.key.providerType());
    }

    /**
     * Whether both components have the same provider and configuration, whatever their ids and the order of the values
     * of their configuration: Keycloak does not keep that order.
     */
    public boolean hasSameContentAs(final Component other) {
        return Objects.equals(this.providerId, other.providerId)
                && unordered(this.config).equals(unordered(other.config));
    }

    private static Map<String, Set<String>> unordered(final Map<String, List<String>> config) {
        Map<String, Set<String>> unordered = new HashMap<>();
        config.forEach((name, values) -> unordered.put(name, values == null ? Set.of() : new HashSet<>(values)));
        return unordered;
    }

    public Component mergedWith(final Component desired) {
        Map<String, List<String>> mergedConfig = new LinkedHashMap<>(this.config);
        mergedConfig.putAll(desired.config);
        return new Component(
                this.key, this.id, desired.providerId != null ? desired.providerId : this.providerId, mergedConfig);
    }

    private static Map<String, List<String>> copyOf(final Map<String, List<String>> config) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        config.forEach((name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
