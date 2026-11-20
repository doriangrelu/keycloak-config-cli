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

package io.github.doriangrelu.keycloak.config.domain.scopemapping.model;

import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Roles a client or a client scope may put in its tokens: realm roles ({@code scopeMappings}), or roles of one client
 * ({@code clientScopeMappings}). Identified by its holder and, for client roles, the client owning the roles.
 *
 * <p>In {@code full} managed mode, the declared roles are the exact roles of the mapping; otherwise they are added to
 * the current ones.
 */
public record ScopeMapping(Key key, Set<String> roles) implements Resource<ScopeMapping.Key> {

    /** What holds a scope mapping. */
    public enum HolderType {
        CLIENT,
        CLIENT_SCOPE
    }

    /**
     * Identity of a scope mapping.
     *
     * @param holder client id of the client, or name of the client scope, holding the mapping
     * @param roleClientId client id of the client owning the roles, {@code null} for realm roles
     */
    public record Key(HolderType holderType, String holder, String roleClientId) {

        public Key {
            Objects.requireNonNull(holderType, "holderType");
            Objects.requireNonNull(holder, "holder");
        }

        public static Key ofClient(final String clientId, final String roleClientId) {
            return new Key(HolderType.CLIENT, clientId, roleClientId);
        }

        public static Key ofClientScope(final String clientScope, final String roleClientId) {
            return new Key(HolderType.CLIENT_SCOPE, clientScope, roleClientId);
        }

        public boolean isRealmRoles() {
            return this.roleClientId == null;
        }
    }

    /** Mappings Keycloak creates in every realm. */
    private static final Set<Key> BUILT_IN =
            Set.of(Key.ofClientScope("offline_access", null), Key.ofClient("account-console", "account"));

    public ScopeMapping {
        Objects.requireNonNull(key, "key");
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    @Override
    public String stateName() {
        return this.key.holder();
    }

    /**
     * Whether Keycloak created this mapping in every realm, never deleted (ADR-007): the {@code offline_access} role of
     * the {@code offline_access} scope, and the roles of {@code account} given to {@code account-console}.
     */
    public boolean isOwnedByKeycloak() {
        return BUILT_IN.contains(this.key);
    }

    /** This mapping once {@code desired} is applied on it: its roles are the declared ones (ADR-007). */
    public ScopeMapping mergedWith(final ScopeMapping desired) {
        return new ScopeMapping(this.key, desired.roles);
    }
}
