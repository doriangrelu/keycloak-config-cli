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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Moves the fields of a Keycloak representation that the business object does not model into
 * {@link UnmodeledAttributes}, and back (ADR-002). Uses its own JSON mapper, aligned on the one of the Keycloak client.
 */
@Component
public class UnmodeledAttributesConverter {

    private static final TypeReference<Map<String, Object>> FIELDS = new TypeReference<>() {};

    private final ObjectMapper mapper = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    /**
     * @param modeled fields carried by the business object, or deliberately left out of it
     */
    public UnmodeledAttributes extract(final Object representation, final Set<String> modeled) {
        Map<String, Object> fields = new LinkedHashMap<>(this.mapper.convertValue(representation, FIELDS));
        fields.keySet().removeAll(modeled);
        return fields.isEmpty() ? UnmodeledAttributes.none() : new UnmodeledAttributes(fields);
    }

    /** Writes the unmodeled fields back on a representation built from the business object. */
    public void apply(final UnmodeledAttributes unmodeled, final Object representation) {
        if (unmodeled.isEmpty()) {
            return;
        }
        try {
            this.mapper.updateValue(representation, unmodeled.values());
        } catch (final com.fasterxml.jackson.databind.JsonMappingException invalid) {
            throw new IllegalArgumentException(
                    "Cannot apply unmodeled attributes " + unmodeled.values().keySet(), invalid);
        }
    }
}
