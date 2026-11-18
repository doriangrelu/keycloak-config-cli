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

package io.github.doriangrelu.keycloak.config.domain.clientscope.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A client scope of a realm, identified by its name.
 *
 * <p>In a desired client scope, a {@code null} field is not specified by the import file and keeps its value.
 * Attributes are merged key by key. Declared protocol mappers form the exact set of mappers of the scope: a mapper
 * missing from the file is removed, an existing one is completed by the file.
 *
 * @param protocolMappers protocol mappers, or {@code null} when not specified
 * @param realmDefault whether the scope is a default or optional scope of the realm, as read from Keycloak; such a
 *     scope is never deleted. Not given by import files.
 */
public record ClientScope(
        String name,
        String description,
        String protocol,
        Map<String, String> attributes,
        List<ProtocolMapper> protocolMappers,
        boolean realmDefault,
        UnmodeledAttributes unmodeled)
        implements Resource<String> {

    public ClientScope {
        Objects.requireNonNull(name, "name");
        attributes = attributes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        // Sorted by name: Keycloak and the import files do not list mappers in the same order
        protocolMappers = protocolMappers == null
                ? null
                : protocolMappers.stream()
                        .sorted(Comparator.comparing(ProtocolMapper::name))
                        .toList();
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    @Override
    public String key() {
        return this.name;
    }

    @Override
    public String stateName() {
        return this.name;
    }

    /** This client scope once {@code desired} is applied on it. */
    public ClientScope mergedWith(final ClientScope desired) {
        return new ClientScope(
                this.name,
                desired.description != null ? desired.description : this.description,
                desired.protocol != null ? desired.protocol : this.protocol,
                ProtocolMapper.mergeConfig(this.attributes, desired.attributes),
                mergeProtocolMappers(desired.protocolMappers),
                this.realmDefault,
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    /** The same client scope without its protocol mappers: what the scope itself is updated with. */
    public ClientScope withoutProtocolMappers() {
        return new ClientScope(
                this.name, this.description, this.protocol, this.attributes, null, this.realmDefault, this.unmodeled);
    }

    private List<ProtocolMapper> mergeProtocolMappers(final List<ProtocolMapper> desired) {
        if (desired == null) {
            return this.protocolMappers;
        }
        Map<String, ProtocolMapper> current = this.protocolMappers == null
                ? Map.of()
                : this.protocolMappers.stream()
                        .collect(Collectors.toMap(ProtocolMapper::name, Function.identity(), (first, second) -> first));
        List<ProtocolMapper> merged = new ArrayList<>();
        for (ProtocolMapper wanted : desired) {
            ProtocolMapper existing = current.get(wanted.name());
            merged.add(existing == null ? wanted : existing.mergedWith(wanted));
        }
        return merged;
    }
}
