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

package io.github.doriangrelu.keycloak.config.domain.identityprovider.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Objects;

/**
 * An identity provider of a realm, identified by its alias.
 *
 * <p>In a desired identity provider, a {@code null} field is not specified by the import file and keeps its value,
 * except the login flows: an undeclared first broker login flow is reset to the one of Keycloak, an undeclared post
 * broker login flow is removed. A declared {@code config} replaces the current one.
 */
public record IdentityProvider(
        String alias, String firstBrokerLoginFlowAlias, String postBrokerLoginFlowAlias, UnmodeledAttributes unmodeled)
        implements Resource<String> {

    /** First broker login flow of Keycloak, used when an identity provider declares none. */
    public static final String DEFAULT_FIRST_BROKER_LOGIN_FLOW = "first broker login";

    public IdentityProvider {
        Objects.requireNonNull(alias, "alias");
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    @Override
    public String key() {
        return this.alias;
    }

    @Override
    public String protectedName() {
        return this.alias;
    }

    /** This identity provider once {@code desired} is applied on it. */
    public IdentityProvider mergedWith(final IdentityProvider desired) {
        return new IdentityProvider(
                this.alias,
                desired.firstBrokerLoginFlowAlias != null
                        ? desired.firstBrokerLoginFlowAlias
                        : DEFAULT_FIRST_BROKER_LOGIN_FLOW,
                desired.postBrokerLoginFlowAlias,
                this.unmodeled.overriddenBy(desired.unmodeled));
    }
}
