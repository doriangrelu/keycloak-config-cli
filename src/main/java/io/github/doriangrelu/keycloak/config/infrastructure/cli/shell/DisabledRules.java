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

package io.github.doriangrelu.keycloak.config.infrastructure.cli.shell;

import io.github.doriangrelu.keycloak.config.domain.definition.rules.Disablement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** The rules the organisation switched off, as the shell shows them. */
final class DisabledRules {

    private DisabledRules() {}

    /** Nothing when no rule is switched off; otherwise a title and a line per disablement. */
    static List<String> lines(final List<Disablement> disablements) {
        List<String> lines = new ArrayList<>();
        if (disablements.isEmpty()) {
            return lines;
        }
        lines.add("Disabled rules:");
        for (Disablement disablement : disablements) {
            String scope = disablement.forElements().isEmpty()
                    ? "all"
                    : String.join(", ", new TreeSet<>(disablement.forElements()));
            lines.add("  %s (%s): %s".formatted(disablement.id(), scope, disablement.reason()));
        }
        return lines;
    }
}
