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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnmodeledAttributesConverterTest {

    /** A representation with one modeled field and two fields the business object does not know. */
    public static class Representation {
        private String name;
        private Boolean newFlag;
        private List<String> newList;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getNewFlag() {
            return this.newFlag;
        }

        public void setNewFlag(Boolean newFlag) {
            this.newFlag = newFlag;
        }

        public List<String> getNewList() {
            return this.newList;
        }

        public void setNewList(List<String> newList) {
            this.newList = newList;
        }
    }

    private final UnmodeledAttributesConverter converter = new UnmodeledAttributesConverter();

    @Test
    void extractsTheFieldsThatAreNotModeled() {
        Representation representation = new Representation();
        representation.setName("admin");
        representation.setNewFlag(true);
        representation.setNewList(List.of("a"));

        UnmodeledAttributes unmodeled = this.converter.extract(representation, Set.of("name"));

        assertThat(unmodeled.values())
                .containsExactlyInAnyOrderEntriesOf(Map.of("newFlag", true, "newList", List.of("a")));
    }

    @Test
    void ignoresFieldsWithoutValue() {
        Representation representation = new Representation();
        representation.setName("admin");

        assertThat(this.converter.extract(representation, Set.of("name")).isEmpty())
                .isTrue();
    }

    @Test
    void writesTheFieldsBack() {
        Representation representation = new Representation();
        representation.setName("admin");

        this.converter.apply(new UnmodeledAttributes(Map.of("newFlag", true, "newList", List.of("a"))), representation);

        assertThat(representation.getName()).isEqualTo("admin");
        assertThat(representation.getNewFlag()).isTrue();
        assertThat(representation.getNewList()).containsExactly("a");
    }
}
