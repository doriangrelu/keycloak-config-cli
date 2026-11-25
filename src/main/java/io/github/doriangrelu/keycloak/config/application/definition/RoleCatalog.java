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

import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The roles a group can be given: those of the folder, then those Keycloak creates by itself, which the files need not
 * define (the same ones the {@code group.defined-roles} rule accepts).
 */
public final class RoleCatalog {

    private static final String DEFAULT_ROLES = "default-roles-";
    private static final String REALM_PLACEHOLDER = "realm_name_placeholder";

    private RoleCatalog() {}

    /** Realm roles first, then the roles of each client; each part sorted, the roles of the folder before Keycloak's. */
    public static List<AssignableRole> of(final RealmDefinition realm, final BuiltIns builtIns) {
        TreeSet<String> realmRoles = new TreeSet<>();
        Map<String, TreeSet<String>> clientRoles = new TreeMap<>();
        for (RoleDefinition role :
                realm.roles().stream().map(defined -> defined.definition()).toList()) {
            if (role.clientId().isEmpty()) {
                realmRoles.add(role.name().raw());
            } else {
                clientRoles
                        .computeIfAbsent(role.clientId().get().raw(), id -> new TreeSet<>())
                        .add(role.name().raw());
            }
        }
        List<AssignableRole> catalogue = new ArrayList<>();
        realmRoles.forEach(name -> catalogue.add(new AssignableRole(Optional.empty(), name, false)));
        clientRoles.forEach((client, names) ->
                names.forEach(name -> catalogue.add(new AssignableRole(Optional.of(client), name, false))));

        TreeSet<String> keycloakRealmRoles = new TreeSet<>(builtIns.realmRoles());
        keycloakRealmRoles.remove(DEFAULT_ROLES + REALM_PLACEHOLDER);
        realm.realm()
                .filter(name -> !name.isTemplated())
                .map(TemplatedValue::raw)
                .ifPresent(name -> keycloakRealmRoles.add(DEFAULT_ROLES + name.toLowerCase(Locale.ROOT)));
        keycloakRealmRoles.stream()
                .filter(name -> !realmRoles.contains(name))
                .forEach(name -> catalogue.add(new AssignableRole(Optional.empty(), name, true)));
        new TreeMap<>(builtIns.clientRoles())
                .forEach((client, names) -> new TreeSet<>(names)
                        .stream()
                                .filter(name -> !clientRoles
                                        .getOrDefault(client, new TreeSet<>())
                                        .contains(name))
                                .forEach(name -> catalogue.add(new AssignableRole(Optional.of(client), name, true))));
        return catalogue;
    }
}
