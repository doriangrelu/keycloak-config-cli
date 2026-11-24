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

import java.util.Objects;

/**
 * One answer offered by a question, with what it means for someone who does not know Keycloak.
 *
 * @param value what the answer gives back
 * @param label the answer, shown in the list
 * @param explanation what choosing it means
 */
public record Choice<T>(T value, String label, String explanation) {

    public Choice {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(explanation, "explanation");
    }
}
