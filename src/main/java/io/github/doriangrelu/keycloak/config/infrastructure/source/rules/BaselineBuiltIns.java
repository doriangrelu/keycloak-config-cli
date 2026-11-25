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

package io.github.doriangrelu.keycloak.config.infrastructure.source.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakBuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The roles and clients of the reference realm shipped for a Keycloak version ({@code baseline/<version>/realm/}
 * {@code realm.json}); the most recent version when the one asked for is not shipped. Read once, on first use.
 */
public final class BaselineBuiltIns implements KeycloakBuiltIns {

    private static final String PATTERN = "classpath*:baseline/*/realm/realm.json";

    private final String keycloakVersion;
    private BuiltIns builtIns;

    public BaselineBuiltIns(final String keycloakVersion) {
        this.keycloakVersion = keycloakVersion;
    }

    @Override
    public synchronized BuiltIns builtIns() {
        if (this.builtIns == null) {
            this.builtIns = read();
        }
        return this.builtIns;
    }

    /** The highest version, comparing the dot-separated parts as numbers. */
    static String mostRecent(final List<String> versions) {
        return versions.stream()
                .max(Comparator.comparing(BaselineBuiltIns::parts, BaselineBuiltIns::compare))
                .orElseThrow();
    }

    private static int[] parts(final String version) {
        return Arrays.stream(version.split("\\."))
                .mapToInt(part ->
                        !part.isEmpty() && part.chars().allMatch(Character::isDigit) ? Integer.parseInt(part) : 0)
                .toArray();
    }

    private static int compare(final int[] left, final int[] right) {
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private BuiltIns read() {
        try {
            Map<String, Resource> byVersion = new LinkedHashMap<>();
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
                String[] segments = resource.getURL().getPath().split("/");
                byVersion.put(segments[segments.length - 3], resource);
            }
            if (byVersion.isEmpty()) {
                return BuiltIns.none();
            }
            String version = byVersion.containsKey(this.keycloakVersion)
                    ? this.keycloakVersion
                    : mostRecent(new ArrayList<>(byVersion.keySet()));
            try (InputStream stream = byVersion.get(version).getInputStream()) {
                return parse(new ObjectMapper().readTree(stream));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The reference realm of Keycloak cannot be read", e);
        }
    }

    private static BuiltIns parse(final JsonNode realm) {
        Set<String> clientIds = new LinkedHashSet<>();
        realm.path("clients")
                .forEach(client -> clientIds.add(client.path("clientId").asText()));
        Set<String> realmRoles = new LinkedHashSet<>();
        realm.path("roles")
                .path("realm")
                .forEach(role -> realmRoles.add(role.path("name").asText()));
        Map<String, Set<String>> clientRoles = new LinkedHashMap<>();
        realm.path("roles").path("client").properties().forEach(entry -> {
            Set<String> names = new LinkedHashSet<>();
            entry.getValue().forEach(role -> names.add(role.path("name").asText()));
            clientRoles.put(entry.getKey(), names);
        });
        return new BuiltIns(clientIds, realmRoles, clientRoles);
    }
}
