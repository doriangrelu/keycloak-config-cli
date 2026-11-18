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

package io.github.doriangrelu.keycloak.config.domain.reconcile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BinaryOperator;
import org.junit.jupiter.api.Test;

class ChangesTest {

    record Item(String key, String value) implements Resource<String> {
        @Override
        public String stateName() {
            return this.key;
        }
    }

    /** A desired value replaces the actual one; a null desired value keeps it, like a partial import. */
    private static final BinaryOperator<Item> PATCH =
            (actual, desired) -> new Item(actual.key(), desired.value() == null ? actual.value() : desired.value());

    @Test
    void createsWhatIsMissing() {
        List<Change<Item>> changes = Changes.toApply(List.of(new Item("a", "1")), List.of(), PATCH);

        assertThat(changes).containsExactly(new Change.Create<>(new Item("a", "1")));
    }

    @Test
    void updatesWhatDiffers() {
        Item current = new Item("a", "1");

        List<Change<Item>> changes = Changes.toApply(List.of(new Item("a", "2")), List.of(current), PATCH);

        assertThat(changes).containsExactly(new Change.Update<>(current, new Item("a", "2")));
    }

    @Test
    void leavesUnchangedResourcesAlone() {
        assertThat(Changes.toApply(List.of(new Item("a", "1")), List.of(new Item("a", "1")), PATCH))
                .isEmpty();
    }

    @Test
    void comparesAfterMergingSoThatOmittedFieldsAreNotAChange() {
        assertThat(Changes.toApply(List.of(new Item("a", null)), List.of(new Item("a", "1")), PATCH))
                .isEmpty();
    }

    @Test
    void neverDeletesWhenApplying() {
        assertThat(Changes.toApply(List.of(), List.of(new Item("a", "1")), PATCH))
                .isEmpty();
    }

    @Test
    void prunesOnlyMissingAndDeletableResources() {
        List<Item> actual = List.of(new Item("kept", "1"), new Item("orphan", "2"), new Item("protected", "3"));

        List<Change<Item>> deletions = Changes.toPrune(
                List.of(new Item("kept", null)), actual, item -> !item.key().equals("protected"));

        assertThat(deletions).containsExactly(new Change.Delete<>(new Item("orphan", "2")));
    }
}
