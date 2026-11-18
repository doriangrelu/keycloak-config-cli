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
 * A mapper of an identity provider, identified by the alias of its identity provider and its name.
 *
 * <p>In a desired mapper, a {@code null} field is not specified by the import file and keeps its value. A declared
 * {@code config} replaces the current one.
 */
public record IdentityProviderMapper(Key key, UnmodeledAttributes unmodeled)
        implements Resource<IdentityProviderMapper.Key> {

    /** Identity of a mapper: the alias of its identity provider and its name. */
    public record Key(String identityProviderAlias, String name) {

        public Key {
            Objects.requireNonNull(identityProviderAlias, "identityProviderAlias");
            Objects.requireNonNull(name, "name");
        }
    }

    public IdentityProviderMapper {
        Objects.requireNonNull(key, "key");
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    @Override
    public String stateName() {
        return this.key.name();
    }

    /** This mapper once {@code desired} is applied on it. */
    public IdentityProviderMapper mergedWith(final IdentityProviderMapper desired) {
        return new IdentityProviderMapper(this.key, this.unmodeled.overriddenBy(desired.unmodeled));
    }
}
