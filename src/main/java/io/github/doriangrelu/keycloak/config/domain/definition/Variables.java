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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The variables of a realm: the placeholders of its files, comments included. */
public final class Variables {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}");

    private Variables() {}

    /**
     * Lists the variables of the files of a realm. A placeholder that is not valid is ignored: the file cannot be read
     * before rendering anyway.
     *
     * @param sourcesByFile the text of each file, as written, by file name
     * @return the variables, sorted by name
     */
    public static List<VariableUse> of(final Map<String, String> sourcesByFile) {
        Map<String, TreeSet<String>> defaults = new TreeMap<>();
        Map<String, TreeSet<String>> files = new TreeMap<>();
        Set<String> withoutDefault = new HashSet<>();
        sourcesByFile.forEach((file, source) -> {
            Matcher matcher = PLACEHOLDER.matcher(source);
            while (matcher.find()) {
                for (TemplatedValue.Variable variable : variablesOf(matcher.group())) {
                    files.computeIfAbsent(variable.name(), name -> new TreeSet<>())
                            .add(file);
                    TreeSet<String> values = defaults.computeIfAbsent(variable.name(), name -> new TreeSet<>());
                    variable.defaultValue().ifPresentOrElse(values::add, () -> withoutDefault.add(variable.name()));
                }
            }
        });
        List<VariableUse> uses = new ArrayList<>();
        files.forEach((name, usedIn) -> uses.add(new VariableUse(
                name, List.copyOf(defaults.get(name)), List.copyOf(usedIn), withoutDefault.contains(name))));
        return uses;
    }

    private static List<TemplatedValue.Variable> variablesOf(final String placeholder) {
        try {
            return TemplatedValue.of(placeholder).variables();
        } catch (InvalidTemplatedValueException e) {
            return List.of();
        }
    }
}
