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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.util.Arrays;

/**
 * What a client is for, in the words of a developer (ADR-008). Each client of a realm has its own type. A client whose
 * flows match no type exactly is {@link #CUSTOM}: only its common fields are edited.
 */
public enum ApplicationType {
    /** A front-end application, running in the browser: it cannot keep a secret. */
    SPA,
    /** A web application with a server, which keeps the secret. */
    WEB_APPLICATION,
    /** A service calling other services with its own rights, without a user. */
    SERVICE,
    /** An API: it receives tokens and logs nobody in. */
    API,
    /** Flows that match no type. */
    CUSTOM;

    /** The type whose flows are exactly these, {@link #CUSTOM} otherwise. */
    public static ApplicationType of(final ClientFlows flows) {
        return Arrays.stream(values())
                .filter(type -> type != CUSTOM)
                .filter(type -> ClientFlows.of(type).equals(flows))
                .findFirst()
                .orElse(CUSTOM);
    }
}
