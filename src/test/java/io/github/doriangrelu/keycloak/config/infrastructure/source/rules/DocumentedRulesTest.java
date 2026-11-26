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

package io.github.doriangrelu.keycloak.config.infrastructure.source.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The YAML examples of the rules page of the shell documentation are valid, and are the ones the page shows. */
class DocumentedRulesTest {

    private static final Path PAGE = Path.of("documentation/shell/rules.md");
    private static final Path RULES = Path.of("src/test/resources/shell/documented-rules.yaml");
    private static final Path ORGANISATION = Path.of("src/test/resources/shell/documented-organisation-rules.yaml");
    private static final Pattern YAML_BLOCK = Pattern.compile("```yaml\\R(.*?)```", Pattern.DOTALL);

    @Test
    void theDocumentedRulesAreRead() throws IOException {
        List<Rule> rules = RuleFileReader.rules(RULES.toString(), read(RULES));

        assertThat(rules).extracting(Rule::id).contains("client.spa.pkce-s256", "role.realm-role.not-admin");
        assertThat(rules).hasSize(9);
    }

    @Test
    void theDocumentedOrganisationFileIsRead() throws IOException {
        OrganisationRules organisation = RuleFileReader.organisation(ORGANISATION.toString(), read(ORGANISATION));

        assertThat(organisation.rules()).hasSize(1);
        assertThat(organisation.disablements()).hasSize(2);
    }

    @Test
    void thePageShowsTheseFilesAndNothingElse() throws IOException {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = YAML_BLOCK.matcher(normalized(Files.readString(PAGE, StandardCharsets.UTF_8)));
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }

        assertThat(blocks).containsExactly(read(RULES), read(ORGANISATION));
    }

    private static String read(final Path file) throws IOException {
        return normalized(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static String normalized(final String text) {
        return text.replace("\r\n", "\n");
    }
}
