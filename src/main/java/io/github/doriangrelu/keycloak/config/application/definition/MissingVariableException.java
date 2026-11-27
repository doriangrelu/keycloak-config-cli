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

/** A variable has no value and nobody can be asked for it, such as in a script. The message holds no value. */
public final class MissingVariableException extends ApplicationException {

    private final String variable;

    public MissingVariableException(final String name) {
        super("Variable '" + name + "' has no value: give it with --var " + name + "=… or KCC_VAR_" + name);
        this.variable = name;
    }

    /** The name of the variable, never a value. */
    public String variable() {
        return this.variable;
    }
}
