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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import java.util.List;

/**
 * What the rules say about an edit.
 *
 * @param blocking the required rules the edit introduces a failure of
 * @param reminders the required rules that already failed before the edit
 * @param advice the failed advice
 * @param unverifiable the rules that could not be checked
 */
public record Verdict(
        List<Finding> blocking, List<Finding> reminders, List<Finding> advice, List<Finding> unverifiable) {

    public Verdict {
        blocking = List.copyOf(blocking);
        reminders = List.copyOf(reminders);
        advice = List.copyOf(advice);
        unverifiable = List.copyOf(unverifiable);
    }

    /** Whether the edit may be written: nothing blocks it. */
    public boolean allowsWriting() {
        return this.blocking.isEmpty();
    }
}
