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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckTest {

    private static final KeyPath KEY = KeyPath.parse("key");

    private static ElementKeys scalar(final String text) {
        return new ElementKeys(Map.of(KEY, new KeyValue.Scalar(TemplatedValue.of(text))));
    }

    private static ElementKeys listed(final String... texts) {
        List<TemplatedValue> values =
                Arrays.stream(texts).map(TemplatedValue::of).toList();
        return new ElementKeys(Map.of(KEY, new KeyValue.Listed(values)));
    }

    private static ElementKeys shapeless() {
        return new ElementKeys(Map.of(KEY, new KeyValue.Shapeless()));
    }

    private static Check check(final Operator operator, final String... operands) {
        return new Check(KEY, operator, List.of(operands));
    }

    @Test
    void equalsOnSameValuePasses() {
        assertThat(check(Operator.EQUALS, "S256").evaluate(scalar("S256"))).isEqualTo(Outcome.PASSED);
    }

    @Test
    void equalsOnOtherValueFails() {
        assertThat(check(Operator.EQUALS, "S256").evaluate(scalar("s256"))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void equalsOnAbsentKeyFails() {
        assertThat(check(Operator.EQUALS, "S256").evaluate(ElementKeys.empty())).isEqualTo(Outcome.FAILED);
    }

    @Test
    void equalsComparesAsText() {
        assertThat(check(Operator.EQUALS, "true").evaluate(scalar("true"))).isEqualTo(Outcome.PASSED);
    }

    @Test
    void equalsOnTemplatedValueIsUnverifiable() {
        assertThat(check(Operator.EQUALS, "S256").evaluate(scalar("{{x}}"))).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void equalsOnShapelessIsUnverifiable() {
        assertThat(check(Operator.EQUALS, "S256").evaluate(shapeless())).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void anObjectKeyIsPresentNotAbsentAndUnverifiableForValues() {
        assertThat(check(Operator.PRESENT).evaluate(shapeless())).isEqualTo(Outcome.PASSED);
        assertThat(check(Operator.ABSENT).evaluate(shapeless())).isEqualTo(Outcome.FAILED);
        assertThat(check(Operator.ONE_OF, "a").evaluate(shapeless())).isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(check(Operator.MATCHES, "a").evaluate(shapeless())).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void equalsOnListIsUnverifiable() {
        assertThat(check(Operator.EQUALS, "S256").evaluate(listed("S256"))).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void oneOfOnValue() {
        Check check = check(Operator.ONE_OF, "a", "b");
        assertThat(check.evaluate(scalar("a"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(scalar("c"))).isEqualTo(Outcome.FAILED);
        assertThat(check.evaluate(ElementKeys.empty())).isEqualTo(Outcome.FAILED);
    }

    @Test
    void oneOfOnListRequiresEveryElementInTheSet() {
        Check check = check(Operator.ONE_OF, "a", "b");
        assertThat(check.evaluate(listed("a", "b"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(listed("a", "c"))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void notOneOfOnValue() {
        Check check = check(Operator.NOT_ONE_OF, "a", "b");
        assertThat(check.evaluate(scalar("c"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(scalar("a"))).isEqualTo(Outcome.FAILED);
        assertThat(check.evaluate(ElementKeys.empty())).isEqualTo(Outcome.PASSED);
    }

    @Test
    void notOneOfOnListRequiresNoElementInTheSet() {
        Check check = check(Operator.NOT_ONE_OF, "a", "b");
        assertThat(check.evaluate(listed("c", "d"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(listed("c", "a"))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void matchesAppliesToTheWholeValue() {
        Check check = check(Operator.MATCHES, "https://.*");
        assertThat(check.evaluate(scalar("https://a"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(scalar("x https://a"))).isEqualTo(Outcome.FAILED);
        assertThat(check.evaluate(ElementKeys.empty())).isEqualTo(Outcome.FAILED);
    }

    @Test
    void matchesOnListRequiresEveryElement() {
        Check check = check(Operator.MATCHES, "https://.*");
        assertThat(check.evaluate(listed("https://a", "https://b"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(listed("https://a", "http://b"))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void noneMatchesOnList() {
        Check check = check(Operator.NONE_MATCHES, "^\\*$");
        assertThat(check.evaluate(listed("https://a/*", "*"))).isEqualTo(Outcome.FAILED);
        assertThat(check.evaluate(listed("https://a/*"))).isEqualTo(Outcome.PASSED);
        assertThat(check.evaluate(ElementKeys.empty())).isEqualTo(Outcome.PASSED);
    }

    @Test
    void noneMatchesOnTemplatedItemIsUnverifiable() {
        assertThat(check(Operator.NONE_MATCHES, "^\\*$").evaluate(listed("{{appUrl}}/*")))
                .isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void literalFailureWinsOverTemplatedItem() {
        assertThat(check(Operator.NONE_MATCHES, "^\\*$").evaluate(listed("{{appUrl}}/*", "*")))
                .isEqualTo(Outcome.FAILED);
        assertThat(check(Operator.ONE_OF, "a").evaluate(listed("{{x}}", "b"))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void presentAndAbsentOnAbsentKey() {
        assertThat(check(Operator.PRESENT).evaluate(ElementKeys.empty())).isEqualTo(Outcome.FAILED);
        assertThat(check(Operator.ABSENT).evaluate(ElementKeys.empty())).isEqualTo(Outcome.PASSED);
    }

    @Test
    void emptyTextAndEmptyListArePresent() {
        assertThat(check(Operator.PRESENT).evaluate(scalar(""))).isEqualTo(Outcome.PASSED);
        assertThat(check(Operator.PRESENT).evaluate(listed())).isEqualTo(Outcome.PASSED);
        assertThat(check(Operator.ABSENT).evaluate(scalar(""))).isEqualTo(Outcome.FAILED);
        assertThat(check(Operator.ABSENT).evaluate(listed())).isEqualTo(Outcome.FAILED);
    }

    @Test
    void presentOnTemplatedValuePasses() {
        assertThat(check(Operator.PRESENT).evaluate(scalar("{{x}}"))).isEqualTo(Outcome.PASSED);
        assertThat(check(Operator.ABSENT).evaluate(scalar("{{x}}"))).isEqualTo(Outcome.FAILED);
    }

    @Test
    void shapelessIsPresentButOtherwiseUnverifiable() {
        assertThat(check(Operator.PRESENT).evaluate(shapeless())).isEqualTo(Outcome.PASSED);
        assertThat(check(Operator.MATCHES, ".*").evaluate(shapeless())).isEqualTo(Outcome.UNVERIFIABLE);
        assertThat(check(Operator.NOT_ONE_OF, "a").evaluate(shapeless())).isEqualTo(Outcome.UNVERIFIABLE);
    }

    @Test
    void invalidPatternIsRejected() {
        assertThatThrownBy(() -> check(Operator.MATCHES, "[")).isInstanceOf(InvalidRulesException.class);
        assertThatThrownBy(() -> check(Operator.NONE_MATCHES, "(")).isInstanceOf(InvalidRulesException.class);
    }

    @Test
    void wrongOperandCountIsRejected() {
        assertThatThrownBy(() -> check(Operator.EQUALS)).isInstanceOf(InvalidRulesException.class);
        assertThatThrownBy(() -> check(Operator.PRESENT, "x")).isInstanceOf(InvalidRulesException.class);
        assertThatThrownBy(() -> check(Operator.ONE_OF)).isInstanceOf(InvalidRulesException.class);
    }

    @Test
    void operatorsAreNamedInKebabCase() {
        assertThat(Operator.named("one-of")).contains(Operator.ONE_OF);
        assertThat(Operator.named("none-matches")).contains(Operator.NONE_MATCHES);
        assertThat(Operator.named("bogus")).isEmpty();
    }

    @Test
    void withReplacesExistingKeys() {
        ElementKeys merged = scalar("a").with(scalar("b"));
        assertThat(merged.get(KEY)).contains(new KeyValue.Scalar(TemplatedValue.of("b")));
    }

    @Test
    void identifierKeys() {
        assertThat(RuleElement.CLIENT.identifierKey()).hasToString("clientId");
        assertThat(RuleElement.ROLE.identifierKey()).hasToString("name");
        assertThat(RuleElement.GROUP.identifierKey()).hasToString("path");
        assertThat(RuleElement.REALM.identifierKey()).hasToString("realm");
    }
}
