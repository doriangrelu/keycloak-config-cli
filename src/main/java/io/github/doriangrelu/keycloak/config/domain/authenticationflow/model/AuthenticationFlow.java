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

package io.github.doriangrelu.keycloak.config.domain.authenticationflow.model;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An authentication flow, identified by its alias.
 *
 * <ul>
 *   <li>A flow of keycloak-config-cli is a top-level flow with its whole tree: its sub-flows, at every level, are part
 *       of it. Keycloak cannot change the executions of a flow in place: a flow whose tree changes is recreated.
 *   <li>A built-in flow, top-level or not, is a flow of Keycloak: it can only be patched, never created nor deleted,
 *       and its sub-flows are separate resources.
 * </ul>
 *
 * <p>The content of a flow is not modeled: {@code definition} holds the flow without its id and alias, executions
 * included, and {@code subFlows} the definition of each sub-flow by alias. {@code authenticatorConfigs} holds the
 * configurations the executions of the tree refer to, needed to create them; they are not part of the structure of the
 * flow, and are reconciled on their own.
 *
 * <p>The {@code id} of a top-level flow is the one Keycloak gave it, or the one a desired flow asks for when it is
 * created: clients refer to flows by id. A recreated flow keeps its id.
 */
public record AuthenticationFlow(
        String alias,
        String id,
        boolean builtIn,
        UnmodeledAttributes definition,
        Map<String, UnmodeledAttributes> subFlows,
        Map<String, AuthenticatorConfig> authenticatorConfigs)
        implements Resource<String> {

    public AuthenticationFlow {
        Objects.requireNonNull(alias, "alias");
        definition = definition == null ? UnmodeledAttributes.none() : definition;
        subFlows = subFlows == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(subFlows));
        authenticatorConfigs = authenticatorConfigs == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(authenticatorConfigs));
    }

    @Override
    public String key() {
        return this.alias;
    }

    @Override
    public String protectedName() {
        return this.alias;
    }

    /** Whether both flows have the same content, sub-flows included, regardless of the authenticator configs. */
    public boolean hasSameStructureAs(final AuthenticationFlow other) {
        return this.builtIn == other.builtIn
                && this.definition.equals(other.definition)
                && this.subFlows.equals(other.subFlows);
    }

    /**
     * This flow once {@code desired} is applied on it: a built-in flow is patched with the declared fields; another
     * flow becomes the desired one when its structure changes, and stays as it is otherwise.
     *
     * @throws InvalidAuthenticationFlowException when the import changes the built-in flag of the flow
     */
    public AuthenticationFlow mergedWith(final AuthenticationFlow desired, final String realm) {
        if (desired.builtIn && !this.builtIn) {
            throw new InvalidAuthenticationFlowException(String.format(
                    "Unable to update flow '%s' in realm '%s': Change built-in flag is not possible",
                    this.alias, realm));
        }
        if (!desired.builtIn && this.builtIn) {
            throw new InvalidAuthenticationFlowException(String.format(
                    "Unable to recreate flow '%s' in realm '%s': Deletion or creation of built-in flows is not"
                            + " possible",
                    this.alias, realm));
        }
        if (this.builtIn) {
            UnmodeledAttributes patched = this.definition.overriddenBy(desired.definition);
            return patched.equals(this.definition)
                    ? this
                    : new AuthenticationFlow(
                            this.alias, this.id, true, patched, this.subFlows, desired.authenticatorConfigs);
        }
        return hasSameStructureAs(desired)
                ? this
                : new AuthenticationFlow(
                        this.alias, this.id, false, desired.definition, desired.subFlows, desired.authenticatorConfigs);
    }
}
