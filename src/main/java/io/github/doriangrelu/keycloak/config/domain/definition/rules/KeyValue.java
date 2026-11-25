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

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;

/** The value of a key of an exported element. */
public sealed interface KeyValue {

    /** Whether at least one value holds a placeholder. */
    boolean isTemplated();

    /**
     * A single value (text, number or boolean, kept as written).
     *
     * @param value the value
     */
    record Scalar(TemplatedValue value) implements KeyValue {
        @Override
        public boolean isTemplated() {
            return this.value.isTemplated();
        }
    }

    /**
     * A list of values.
     *
     * @param values the values
     */
    record Listed(List<TemplatedValue> values) implements KeyValue {
        public Listed {
            values = List.copyOf(values);
        }

        @Override
        public boolean isTemplated() {
            return this.values.stream().anyMatch(TemplatedValue::isTemplated);
        }
    }

    /** An object or a list of objects: it can only be tested for presence. */
    record Shapeless() implements KeyValue {
        @Override
        public boolean isTemplated() {
            return false;
        }
    }
}
