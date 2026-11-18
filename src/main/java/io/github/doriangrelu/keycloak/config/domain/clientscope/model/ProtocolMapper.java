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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A protocol mapper of a client scope, identified by its name within the scope.
 *
 * @param protocol protocol, or {@code null} when not specified
 * @param protocolMapper mapper type, or {@code null} when not specified
 * @param config configuration, or {@code null} when not specified; when specified, merged key by key
 */
public record ProtocolMapper(
        String name,
        String protocol,
        String protocolMapper,
        Map<String, String> config,
        UnmodeledAttributes unmodeled) {

    public ProtocolMapper {
        Objects.requireNonNull(name, "name");
        config = config == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    /** This mapper once {@code desired} is applied: unspecified fields are kept, config keys are merged. */
    public ProtocolMapper mergedWith(final ProtocolMapper desired) {
        return new ProtocolMapper(
                this.name,
                desired.protocol != null ? desired.protocol : this.protocol,
                desired.protocolMapper != null ? desired.protocolMapper : this.protocolMapper,
                mergeConfig(this.config, desired.config),
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    static Map<String, String> mergeConfig(final Map<String, String> current, final Map<String, String> desired) {
        if (desired == null) {
            return current;
        }
        Map<String, String> merged = new LinkedHashMap<>(current == null ? Map.of() : current);
        merged.putAll(desired);
        return merged;
    }
}
