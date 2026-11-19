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

package io.github.doriangrelu.keycloak.config.domain.realm.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The settings of a realm, identified by its name: everything an import declares at the top level of the realm except
 * the resources imported on their own (clients, roles, flows...). They are not modeled.
 *
 * <p>The settings an import omits keep their value. Declared {@code attributes} replace the current ones, except the
 * attributes where keycloak-config-cli keeps its remote state and checksums.
 */
public record RealmSettings(String realm, UnmodeledAttributes settings) implements Resource<String> {

    static final String ATTRIBUTES = "attributes";

    public RealmSettings {
        Objects.requireNonNull(realm, "realm");
        settings = settings == null ? UnmodeledAttributes.none() : settings;
    }

    @Override
    public String key() {
        return this.realm;
    }

    @Override
    public String stateName() {
        return this.realm;
    }

    /**
     * These settings once {@code desired} is applied on them.
     *
     * @param ownAttributePrefix prefix of the attributes of keycloak-config-cli, kept when the import declares
     *     attributes
     */
    public RealmSettings mergedWith(final RealmSettings desired, final String ownAttributePrefix) {
        UnmodeledAttributes merged = this.settings.overriddenBy(desired.settings);
        if (desired.settings.values().get(ATTRIBUTES) instanceof Map<?, ?> declared
                && this.settings.values().get(ATTRIBUTES) instanceof Map<?, ?> current) {
            Map<Object, Object> attributes = new LinkedHashMap<>(declared);
            current.forEach((name, value) -> {
                if (String.valueOf(name).startsWith(ownAttributePrefix)) {
                    attributes.put(name, value);
                }
            });
            Map<String, Object> values = new LinkedHashMap<>(merged.values());
            values.put(ATTRIBUTES, attributes);
            merged = new UnmodeledAttributes(values);
        }
        return new RealmSettings(this.realm, merged);
    }
}
