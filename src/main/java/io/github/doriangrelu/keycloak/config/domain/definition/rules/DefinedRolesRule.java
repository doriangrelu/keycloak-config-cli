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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every role a group gives must exist: defined in the realm files, or created by Keycloak itself (see
 * {@link BuiltIns}). The client of a client role must be defined or built in as well.
 */
public final class DefinedRolesRule implements Rule {

    private static final KeyPath REALM_ROLES = KeyPath.parse("realmRoles");
    private static final String CLIENT_ROLES = "clientRoles";

    @Override
    public String id() {
        return "group.defined-roles";
    }

    @Override
    public RuleElement element() {
        return RuleElement.GROUP;
    }

    @Override
    public Severity severity() {
        return Severity.REQUIRED;
    }

    @Override
    public String message() {
        return "A group gives a role that is defined neither in the realm files nor by Keycloak.";
    }

    @Override
    public boolean appliesTo(final ElementKeys keys) {
        return true;
    }

    @Override
    public Outcome check(final ElementKeys keys, final RealmContext context) {
        boolean failed = false;
        boolean unverifiable = false;
        for (Map.Entry<KeyPath, KeyValue> entry : keys.values().entrySet()) {
            List<String> segments = entry.getKey().segments();
            boolean realmRoles = entry.getKey().equals(REALM_ROLES);
            boolean clientRoles = segments.size() == 2 && segments.getFirst().equals(CLIENT_ROLES);
            if (!(realmRoles || clientRoles) || !(entry.getValue() instanceof KeyValue.Listed listed)) {
                continue;
            }
            String client = clientRoles ? segments.get(1) : null;
            if (client != null) {
                if (TemplatedValue.of(client).isTemplated()) {
                    unverifiable = true;
                    continue;
                }
                if (!clientKnown(client, context)) {
                    failed = true;
                    continue;
                }
            }
            for (TemplatedValue role : listed.values()) {
                if (role.isTemplated()) {
                    unverifiable = true;
                } else if (!(client == null
                        ? realmRoleKnown(role.raw(), context)
                        : clientRoleKnown(client, role.raw(), context))) {
                    failed = true;
                }
            }
        }
        if (failed) {
            return Outcome.FAILED;
        }
        return unverifiable ? Outcome.UNVERIFIABLE : Outcome.PASSED;
    }

    private static boolean clientKnown(final String clientId, final RealmContext context) {
        return context.builtIns().clientIds().contains(clientId)
                || context.builtIns().clientRoles().containsKey(clientId)
                || context.realm().client(clientId).isPresent();
    }

    private static boolean realmRoleKnown(final String name, final RealmContext context) {
        return context.builtIns().realmRoles().contains(name)
                || isDefaultRoleOfRealm(name, context)
                || roles(context)
                        .anyMatch(role ->
                                role.clientId().isEmpty() && role.name().raw().equals(name));
    }

    /** Keycloak creates {@code default-roles-<realm>} (lowercased) in every realm; the reference realm only has a placeholder. */
    private static boolean isDefaultRoleOfRealm(final String name, final RealmContext context) {
        return context.realm().realm().filter(realm -> !realm.isTemplated()).stream()
                .anyMatch(realm -> name.equals("default-roles-" + realm.raw().toLowerCase(java.util.Locale.ROOT)));
    }

    private static boolean clientRoleKnown(final String clientId, final String name, final RealmContext context) {
        return context.builtIns().clientRoles().getOrDefault(clientId, Set.of()).contains(name)
                || roles(context)
                        .anyMatch(
                                role -> role.clientId().map(TemplatedValue::raw).equals(Optional.of(clientId))
                                        && role.name().raw().equals(name));
    }

    private static Stream<RoleDefinition> roles(final RealmContext context) {
        return context.realm().roles().stream().map(defined -> defined.definition());
    }
}
