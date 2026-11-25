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
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Finding;
import java.util.List;

/** The rules refuse a change: it introduces the failure of a required rule. Nothing is written. */
public final class RuleViolationException extends ApplicationException {

    public RuleViolationException(final List<Finding> blocking) {
        super(message(blocking));
    }

    private static String message(final List<Finding> blocking) {
        StringBuilder text = new StringBuilder("The rules refuse this change:");
        for (Finding finding : blocking) {
            text.append("\n- ").append(finding.ruleId()).append(": ").append(finding.message());
        }
        return text.toString();
    }
}
