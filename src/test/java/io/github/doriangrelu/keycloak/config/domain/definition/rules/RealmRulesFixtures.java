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

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RealmRulesFixtures {

    private RealmRulesFixtures() {}

    static Defined<ClientDefinition> client(final String clientId, final int line) {
        return new Defined<>(
                new ClientDefinition(
                        TemplatedValue.of(clientId),
                        Optional.empty(),
                        Optional.empty(),
                        ApplicationType.SPA,
                        List.of(),
                        List.of(),
                        Map.of()),
                new Origin("clients.yaml", line));
    }

    static Defined<RoleDefinition> role(final String name, final String clientId, final int line) {
        return new Defined<>(
                new RoleDefinition(
                        TemplatedValue.of(name),
                        Optional.ofNullable(clientId).map(TemplatedValue::of),
                        Optional.empty()),
                new Origin("roles.yaml", line));
    }

    static Defined<GroupDefinition> group(final String path, final int line) {
        return new Defined<>(
                new GroupDefinition(
                        path, TemplatedValue.of(path.substring(path.lastIndexOf('/') + 1)), Set.of(), Map.of()),
                new Origin("groups.yaml", line));
    }

    static RealmDefinition realm(
            final List<Defined<ClientDefinition>> clients,
            final List<Defined<RoleDefinition>> roles,
            final List<Defined<GroupDefinition>> groups) {
        return new RealmDefinition(
                "realm",
                Optional.empty(),
                clients,
                roles,
                groups,
                Set.of(ElementType.CLIENT, ElementType.ROLE, ElementType.GROUP),
                List.of());
    }

    static KeyValue scalar(final String text) {
        return new KeyValue.Scalar(TemplatedValue.of(text));
    }

    static KeyValue listed(final String... texts) {
        return new KeyValue.Listed(Arrays.stream(texts).map(TemplatedValue::of).toList());
    }

    static ElementKeys keys(final Object... pairs) {
        Map<KeyPath, KeyValue> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(KeyPath.parse((String) pairs[i]), (KeyValue) pairs[i + 1]);
        }
        return new ElementKeys(values);
    }
}
