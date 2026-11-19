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

import io.github.doriangrelu.keycloak.config.domain.common.DomainException;

/**
 * An authentication flow of an import file breaks a rule: a missing sub-flow, a built-in flag that changes, an
 * authenticator set on a sub-flow that is not a form.
 */
public class InvalidAuthenticationFlowException extends DomainException {

    public InvalidAuthenticationFlowException(final String message) {
        super(message);
    }
}
