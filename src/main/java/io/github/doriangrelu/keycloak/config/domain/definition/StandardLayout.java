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

import java.util.List;
import java.util.Set;

/**
 * The standard layout of the folder of a realm: the numbered file that holds each top-level key (ADR-008). The keys
 * that cite roles, groups or clients go to {@code 50-links}, applied after the files that define them.
 */
public final class StandardLayout {

    private static final String REALM = "00-realm";
    private static final String LINKS = "50-links";
    private static final Set<String> LINK_KEYS = Set.of(
            "users", "scopeMappings", "clientScopeMappings", "defaultGroups", "identityProviderMappers", "defaultRole");

    private StandardLayout() {}

    /** The name, without extension, of the standard file of a top-level key: the realm file for any other key. */
    public static String fileStemOf(final String key) {
        if (LINK_KEYS.contains(key)) {
            return LINKS;
        }
        return switch (key) {
            case "clients" -> "20-clients";
            case "roles" -> "30-roles";
            case "groups" -> "40-groups";
            default -> REALM;
        };
    }

    /** The standard files, without extension, in the order of their numbers. */
    public static List<String> stems() {
        return List.of(REALM, "20-clients", "30-roles", "40-groups", LINKS);
    }
}
