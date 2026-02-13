/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
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

package io.github.doriangrelu.keycloak.config.util;

import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeycloakUtil {
    private KeycloakUtil() {
    }

    public static boolean doesProtected(final String name) {
        final Collection<String> protectedPatterns = ExecutionContextHolder.context().get("protected", String.class);

        return protectedPatterns.stream().map(Pattern::compile)
                .map(pattern -> pattern.matcher(name))
                .anyMatch(Matcher::matches);
    }

    private static boolean isDefaultResource(String prefix, String property1, String property2) {
        if (property1 == null || property2 == null) {
            return false;
        }

        return Objects.equals(
                String.format("${%s_%s}", prefix, property1),
                property2
        ) || Objects.equals(
                // offline_access is named '${role_offline-access}' by default
                String.format("${%s_%s}", prefix, property1.replace("_", "-")),
                property2
        );
    }

    public static boolean isDefaultRole(RoleRepresentation role) {
        if (StringUtils.startsWith(role.getName(), "default-roles-") && role.getDescription().equals("${role_default-roles}")) {
            return true;
        }

        return isDefaultResource("role", role.getName(), role.getDescription());
    }

    public static boolean isDefaultClient(ClientRepresentation client) {
        return isDefaultResource("client", client.getClientId(), client.getName());
    }
}
