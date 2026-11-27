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

import io.github.doriangrelu.keycloak.config.domain.common.HiddenValues;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The settings of a realm, identified by its name: everything an import declares at the top level of the realm except
 * the resources imported on their own (clients, roles, flows...). They are not modeled.
 *
 * <p>The settings an import omits keep their value. Declared {@code attributes} replace the current ones.
 */
public record RealmSettings(String realm, UnmodeledAttributes settings) implements Resource<String> {

    static final String ATTRIBUTES = "attributes";

    static final String ENABLED = "enabled";

    static final String SMTP_SERVER = "smtpServer";

    public RealmSettings {
        Objects.requireNonNull(realm, "realm");
        settings = settings == null ? UnmodeledAttributes.none() : settings;
    }

    @Override
    public String key() {
        return this.realm;
    }

    @Override
    public String protectedName() {
        return this.realm;
    }

    /**
     * These settings without the values of {@code reference} (normalization). The {@code attributes} are compared one
     * by one, and whether the realm is enabled is always kept.
     */
    public RealmSettings withoutValuesOf(final RealmSettings reference) {
        return new RealmSettings(
                this.realm, this.settings.differingFrom(reference.settings, Set.of(ATTRIBUTES), Set.of(ENABLED)));
    }

    /**
     * These settings once {@code desired} is applied on them. Declared {@code attributes} replace the current ones; the
     * attributes former versions of keycloak-config-cli kept in the realm are removed, declared or not (ADR-007).
     *
     * @param obsoleteAttributePrefix prefix of the attributes keycloak-config-cli no longer uses
     */
    public RealmSettings mergedWith(final RealmSettings desired, final String obsoleteAttributePrefix) {
        UnmodeledAttributes merged = this.settings.overriddenBy(desired.settings);
        if (merged.values().get(ATTRIBUTES) instanceof Map<?, ?> attributes
                && attributes.keySet().stream()
                        .anyMatch(name -> String.valueOf(name).startsWith(obsoleteAttributePrefix))) {
            Map<Object, Object> kept = new LinkedHashMap<>(attributes);
            kept.keySet().removeIf(name -> String.valueOf(name).startsWith(obsoleteAttributePrefix));
            merged = withAttributes(merged, kept);
        }
        return new RealmSettings(this.realm, merged);
    }

    /**
     * The fields of {@code declared} whose value Keycloak hides in these current settings: the entries of the SMTP
     * server ({@code smtpServer.password}).
     */
    public java.util.List<String> hiddenFieldsOf(final RealmSettings declared) {
        return HiddenValues.hiddenFields(this.settings, declared.settings, SMTP_SERVER);
    }

    /** These settings as they compare with {@code current}: the values Keycloak hides in it are taken from it. */
    public RealmSettings withHiddenValuesOf(final RealmSettings current) {
        UnmodeledAttributes comparable = HiddenValues.withHiddenValuesOf(current.settings, this.settings, SMTP_SERVER);
        return comparable == this.settings ? this : new RealmSettings(this.realm, comparable);
    }

    private static UnmodeledAttributes withAttributes(
            final UnmodeledAttributes settings, final Map<Object, Object> attributes) {
        Map<String, Object> values = new LinkedHashMap<>(settings.values());
        values.put(ATTRIBUTES, attributes);
        return new UnmodeledAttributes(values);
    }
}
