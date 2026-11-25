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

/** A client secret written as a value, or with a default value: it is always a variable without default. */
public final class HardCodedSecretException extends ApplicationException {

    static final String MESSAGE = "A client secret is a variable ({{name}}), never a value written in the file";

    static final String DEFAULT_MESSAGE =
            "A secret variable has no default value: a default would be a secret written in the file";

    public HardCodedSecretException() {
        super(MESSAGE);
    }

    private HardCodedSecretException(final String message) {
        super(message);
    }

    /** The secret is a variable, but it carries a default value. */
    static HardCodedSecretException withDefault() {
        return new HardCodedSecretException(DEFAULT_MESSAGE);
    }
}
