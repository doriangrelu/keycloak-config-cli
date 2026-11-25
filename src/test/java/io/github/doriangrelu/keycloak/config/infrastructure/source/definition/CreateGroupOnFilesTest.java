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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.definition.CreateGroup;
import io.github.doriangrelu.keycloak.config.application.definition.GroupAnswers;
import io.github.doriangrelu.keycloak.config.application.definition.ParentGroupElsewhereException;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.application.definition.SaveElement;
import io.github.doriangrelu.keycloak.config.application.definition.ScriptedQuestions;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakBuiltIns;
import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Creating a group on the real files, when the groups of a realm are spread over several files. */
class CreateGroupOnFilesTest {

    private static final String REALM = "realm: demo\n";

    @TempDir
    Path root;

    private Path folder;
    private RealmDefinitionFiles files;

    @BeforeEach
    void setUp() throws IOException {
        this.folder = Files.createDirectories(this.root.resolve("demo"));
        this.files = new RealmDefinitionFiles(this.root);
        write("40-groups.yaml", REALM + "groups:\n  - name: team\n");
        write("41-groups.yaml", REALM + "groups:\n  - name: ops\n");
    }

    private void write(final String name, final String content) throws IOException {
        Files.writeString(this.folder.resolve(name), content, StandardCharsets.UTF_8);
    }

    private String read(final String name) throws IOException {
        return Files.readString(this.folder.resolve(name), StandardCharsets.UTF_8);
    }

    private CreateGroup useCase() {
        RuleFiles ruleFiles = new RuleFiles() {
            @Override
            public List<Rule> base() {
                return List.of();
            }

            @Override
            public OrganisationRules organisation() {
                return OrganisationRules.none();
            }
        };
        KeycloakBuiltIns builtIns = BuiltIns::none;
        RuleBook rules = new RuleBook(ruleFiles, List.of(), builtIns);
        return new CreateGroup(this.files, new SaveElement(this.files, rules), rules);
    }

    private static GroupAnswers subgroup(final String name, final String parent) {
        return new GroupAnswers(
                Optional.of(TemplatedValue.of(name)),
                Optional.of(parent),
                Optional.of(List.of()),
                Optional.empty(),
                false);
    }

    @Test
    void theAdapterAloneFailsOnAParentInAnotherFile() {
        GroupDefinition group = new GroupDefinition("/ops/devs", TemplatedValue.of("devs"), Set.of(), Map.of());

        assertThatThrownBy(() -> this.files.save("demo", group, Optional.<Origin>empty(), ElementKeys.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSubgroupOfAGroupDeclaredInAnotherFileIsRefusedClearly() throws IOException {
        assertThatThrownBy(() -> useCase().create("demo", subgroup("devs", "/ops"), new ScriptedQuestions()))
                .isInstanceOf(ParentGroupElsewhereException.class)
                .hasMessageContaining("/ops")
                .hasMessageContaining("41-groups.yaml")
                .hasMessageContaining("40-groups.yaml");
        assertThat(read("41-groups.yaml")).doesNotContain("devs");
        assertThat(read("40-groups.yaml")).doesNotContain("devs");
    }

    @Test
    void aSubgroupOfAGroupInTheGroupsFileIsWrittenUnderIt() throws IOException {
        useCase().create("demo", subgroup("devs", "/team"), new ScriptedQuestions());

        assertThat(read("40-groups.yaml")).contains("subGroups").contains("devs");
        assertThat(this.files.load("demo").orElseThrow().groups())
                .extracting(defined -> defined.definition().path())
                .contains("/team/devs");
    }
}
