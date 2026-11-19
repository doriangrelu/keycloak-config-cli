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

package io.github.doriangrelu.keycloak.config.application.authenticationflow;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** An authenticator config is declared without an execution using it, or used without being declared. */
public class UnknownAuthenticatorConfigException extends ApplicationException {

    private UnknownAuthenticatorConfigException(final String message) {
        super(message);
    }

    /** The import declares a configuration no execution uses: only an execution can create it. */
    public static UnknownAuthenticatorConfigException notUsed(final String alias) {
        return new UnknownAuthenticatorConfigException(
                String.format("Authenticator Config '%s' not found. Config must be used in execution", alias));
    }

    /** An execution of the import uses a configuration the import does not declare. */
    public static UnknownAuthenticatorConfigException notDeclared(final String alias) {
        return new UnknownAuthenticatorConfigException(
                String.format("Authenticator config '%s' definition not found", alias));
    }
}
