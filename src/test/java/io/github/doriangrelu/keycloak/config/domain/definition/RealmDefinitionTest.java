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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RealmDefinitionTest {

    private static Defined<ClientDefinition> client(final String clientId) {
        return new Defined<>(
                new ClientDefinition(
                        TemplatedValue.of(clientId),
                        Optional.empty(),
                        Optional.empty(),
                        ApplicationType.SPA,
                        List.of(),
                        List.of(),
                        Map.of()),
                new Origin("20-clients.yaml", 3));
    }

    @Test
    void aRealmWithAnUnreadableFileIsReadOnly() {
        RealmDefinition realm = new RealmDefinition(
                "mon-realm",
                Optional.of(TemplatedValue.of("{{env}}-mon-realm")),
                List.of(client("frontend")),
                List.of(),
                List.of(),
                Set.of(ElementType.CLIENT),
                List.of(new UnreadableFile("40-groups.yaml", 2, "unquoted placeholder")));

        assertThat(realm.isReadOnly()).isTrue();
    }

    @Test
    void aClientIsFoundByItsIdAsWritten() {
        RealmDefinition realm = new RealmDefinition(
                "mon-realm",
                Optional.empty(),
                List.of(client("frontend"), client("{{env}}-api")),
                List.of(),
                List.of(),
                Set.of(ElementType.CLIENT),
                List.of());

        assertThat(realm.isReadOnly()).isFalse();
        assertThat(realm.client("{{env}}-api"))
                .map(found -> found.origin().line())
                .contains(3);
        assertThat(realm.client("other")).isEmpty();
    }

    @Test
    void elementsKnowTheirTypeAndTheirFile() {
        assertThat(client("frontend").definition().type()).isEqualTo(ElementType.CLIENT);
        assertThat(ElementType.ROLE.fileName()).isEqualTo("30-roles");
        assertThat(ElementType.GROUP.key()).isEqualTo("groups");
        assertThat(DefinitionFormat.ofFileName("20-clients.yml")).contains(DefinitionFormat.YAML);
        assertThat(DefinitionFormat.ofFileName("notes.txt")).isEmpty();
    }
}
