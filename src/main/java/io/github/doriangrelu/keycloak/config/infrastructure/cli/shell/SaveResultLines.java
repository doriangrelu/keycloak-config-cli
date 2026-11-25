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

import io.github.doriangrelu.keycloak.config.application.definition.SaveResult;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Finding;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** What the shell says about an element written: the file, then what the rules set, advise or could not check. */
final class SaveResultLines {

    private SaveResultLines() {}

    /**
     * @param folder the folder of the realm
     * @param what the kind of element written, such as {@code Client 'shop'}
     * @param result what the save returned
     */
    static List<String> of(final String folder, final String what, final SaveResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("%s written to %s/%s".formatted(what, folder, result.file()));
        result.defaults()
                .values()
                .forEach((key, value) -> lines.add("  set by the rules: %s = %s".formatted(key, text(value))));
        for (Finding finding : result.verdict().advice()) {
            lines.add("  advice %s: %s".formatted(finding.ruleId(), finding.message()));
        }
        for (Finding finding : result.verdict().reminders()) {
            lines.add("  still to fix %s: %s".formatted(finding.ruleId(), finding.message()));
        }
        if (!result.verdict().unverifiable().isEmpty()) {
            lines.add("  not checked (placeholders): "
                    + result.verdict().unverifiable().stream()
                            .map(Finding::ruleId)
                            .collect(Collectors.joining(", ")));
        }
        return lines;
    }

    private static String text(final KeyValue value) {
        return switch (value) {
            case KeyValue.Scalar scalar -> scalar.value().raw();
            case KeyValue.Listed listed ->
                listed.values().stream().map(TemplatedValue::raw).collect(Collectors.joining(", "));
            case KeyValue.Shapeless shapeless -> "(set)";
        };
    }
}
