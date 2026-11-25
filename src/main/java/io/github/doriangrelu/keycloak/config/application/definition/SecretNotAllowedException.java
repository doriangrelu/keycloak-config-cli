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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** A secret is given for a client that does not keep one in the definition files. */
public final class SecretNotAllowedException extends ApplicationException {

    private SecretNotAllowedException(final String message) {
        super(message);
    }

    /** The secret of a front-end application: it runs in the browser. */
    public static SecretNotAllowedException forSpa() {
        return new SecretNotAllowedException("A front-end application cannot keep a secret");
    }

    /** The secret of a custom client: its flows match no type, so the shell leaves it as it is. */
    public static SecretNotAllowedException forCustomClient() {
        return new SecretNotAllowedException("The secret of a custom client is not edited by the shell");
    }
}
