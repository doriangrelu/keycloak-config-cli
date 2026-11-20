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

package io.github.doriangrelu.keycloak.config.domain.requiredaction.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A required action of a realm, identified by its alias.
 *
 * <p>The import replaces every field, like the legacy import did: {@code enabled}, {@code defaultAction} and
 * {@code priority} default to {@code false}, {@code false} and {@code 0} when a file omits them. Only {@code config}
 * keeps its value when not specified.
 *
 * @param config configuration, or {@code null} when not specified
 */
public record RequiredAction(
        String alias,
        String name,
        String providerId,
        boolean enabled,
        boolean defaultAction,
        int priority,
        Map<String, String> config,
        UnmodeledAttributes unmodeled)
        implements Resource<String> {

    /** Required actions Keycloak registers in every realm, by lower-case alias. */
    private static final Set<String> BUILT_IN = Set.of(
            "configure_totp",
            "terms_and_conditions",
            "update_password",
            "update_profile",
            "verify_email",
            "delete_account",
            "update_email",
            "webauthn-register",
            "webauthn-register-passwordless",
            "verify_profile",
            "delete_credential",
            "idp_link",
            "configure_recovery_authn_codes",
            "update_user_locale");

    public RequiredAction {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(providerId, "providerId");
        config = config == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    /**
     * Whether Keycloak registers this required action in every realm, never deleted (ADR-007): it can be disabled
     * instead.
     */
    public boolean isOwnedByKeycloak() {
        return BUILT_IN.contains(this.alias.toLowerCase(Locale.ROOT));
    }

    @Override
    public String key() {
        return this.alias;
    }

    @Override
    public String stateName() {
        return this.alias;
    }

    /** This required action once {@code desired} is applied on it. */
    public RequiredAction mergedWith(final RequiredAction desired) {
        return new RequiredAction(
                desired.alias,
                desired.name,
                desired.providerId,
                desired.enabled,
                desired.defaultAction,
                desired.priority,
                desired.config != null ? desired.config : this.config,
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    /** Keycloak cannot change the provider of a required action: it has to be deleted and created again. */
    public boolean needsRecreationFor(final RequiredAction target) {
        return !this.providerId.equals(target.providerId);
    }
}
