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

import java.util.List;
import java.util.Objects;

/**
 * A realm read from the Keycloak of a target, normalized, as an import document.
 *
 * @param realm the name of the realm
 * @param document the realm in the JSON import format, its secrets replaced by placeholders ({@code {{shopSecret}}});
 *     it never holds a secret value
 * @param variables the names of the variables of the placeholders, sorted
 * @param warnings what the normalization found, ready to be shown: the defaults deleted in Keycloak
 * @param skippedUsers the users from a directory and the service accounts, which are not extracted
 */
public record ExtractedRealm(
        String realm, String document, List<String> variables, List<String> warnings, int skippedUsers) {

    public ExtractedRealm {
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(document, "document");
        variables = variables.stream().sorted().toList();
        warnings = List.copyOf(warnings);
    }

    /** Without the document. */
    @Override
    public String toString() {
        return "ExtractedRealm[realm=" + this.realm + ", variables=" + this.variables + ", warnings="
                + this.warnings.size() + ", skippedUsers=" + this.skippedUsers + "]";
    }
}
