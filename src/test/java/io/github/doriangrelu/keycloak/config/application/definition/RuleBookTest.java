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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuleBookTest {

    @Test
    void rulesAreLoadedOnce() {
        AtomicInteger reads = new AtomicInteger();
        RuleFiles files = new RuleFiles() {
            @Override
            public List<Rule> base() {
                reads.incrementAndGet();
                return List.of(Rules.NO_WILDCARD);
            }

            @Override
            public OrganisationRules organisation() {
                return OrganisationRules.none();
            }
        };
        RuleBook book = new RuleBook(files, List.of(), BuiltIns::none);

        assertThat(book.rules()).isSameAs(book.rules());
        book.engine();

        assertThat(reads).hasValue(1);
    }

    @Test
    void anInvalidFileFailsOnFirstUseAndOnEveryUse() {
        RuleFiles files = new RuleFiles() {
            @Override
            public List<Rule> base() {
                return List.of();
            }

            @Override
            public OrganisationRules organisation() {
                throw new InvalidRulesException("broken");
            }
        };
        RuleBook book = new RuleBook(files, List.of(), BuiltIns::none);

        assertThatThrownBy(book::rules).isInstanceOf(InvalidRulesException.class);
        assertThatThrownBy(book::engine).isInstanceOf(InvalidRulesException.class);
    }
}
