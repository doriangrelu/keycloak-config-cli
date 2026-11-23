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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class MustacheTemplatesTest {

    private final MustacheTemplates templates = new MustacheTemplates(Map.of(
            "realm", "demo",
            "secret", "a\"b\\c",
            "multiline", "line 1\nline 2",
            "html", "<b>R&D</b>",
            "fragment", "{\"xRobotsTag\": \"none\"}"));

    @Test
    void aDefinedVariableIsReplacedByItsValue() {
        assertThat(this.templates.render("realm.json", "{\"realm\": \"{{realm}}\"}"))
                .isEqualTo("{\"realm\": \"demo\"}");
    }

    @Test
    void aDefaultValueIsUsedWhenTheVariableIsNotDefined() {
        assertThat(this.templates.render("realm.json", "\"{{missing:fallback}}\" \"{{realm:other}}\""))
                .isEqualTo("\"fallback\" \"demo\"");
    }

    @Test
    void aDefaultValueMayHoldDotsColonsAndSlashes() {
        assertThat(this.templates.render("realm.json", "\"{{url:https://sso.example.com:8443/auth}}\""))
                .isEqualTo("\"https://sso.example.com:8443/auth\"");
    }

    @Test
    void anEmptyDefaultValueIsAllowed() {
        assertThat(this.templates.render("realm.json", "\"{{missing:}}\"")).isEqualTo("\"\"");
    }

    @Test
    void anUndefinedVariableWithoutDefaultFailsWithItsNameTheFileAndTheLine() {
        assertThatThrownBy(() -> this.templates.render("realm.json", "{\n  \"secret\": \"{{clientSecret}}\"\n}"))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("'clientSecret'")
                .hasMessageContaining("'realm.json'")
                .hasMessageContaining("line 2");
    }

    @Test
    void anInvalidTemplateFailsWithTheFile() {
        assertThatThrownBy(() -> this.templates.render("realm.yaml", "realm: {{#open}}"))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("'realm.yaml'");
    }

    @Test
    void quotesBackslashesAndLineBreaksKeepTheirMeaningInJson() throws Exception {
        String json = this.templates.render(
                "realm.json", "{\"secret\": \"{{secret}}\", \"text\": \"{{multiline}}\", \"html\": \"{{html}}\"}");

        Map<String, Object> parsed = new ObjectMapper().readValue(json, new TypeReference<>() {});

        assertThat(parsed)
                .containsEntry("secret", "a\"b\\c")
                .containsEntry("text", "line 1\nline 2")
                .containsEntry("html", "<b>R&D</b>");
    }

    @Test
    void quotesBackslashesAndLineBreaksKeepTheirMeaningInYaml() {
        String yaml = this.templates.render(
                "realm.yaml", "secret: \"{{secret}}\"\ntext: \"{{multiline}}\"\nhtml: \"{{html}}\"\n");

        Map<String, Object> parsed = new Yaml().load(yaml);

        assertThat(parsed)
                .containsEntry("secret", "a\"b\\c")
                .containsEntry("text", "line 1\nline 2")
                .containsEntry("html", "<b>R&D</b>");
    }

    @Test
    void htmlIsNotEscaped() {
        assertThat(this.templates.render("realm.json", "{{html}}")).isEqualTo("<b>R&D</b>");
    }

    @Test
    void aTripleMustacheInsertsTheValueAsItIs() {
        assertThat(this.templates.render("realm.json", "{\"headers\": {{{fragment}}}}"))
                .isEqualTo("{\"headers\": {\"xRobotsTag\": \"none\"}}");
    }

    @Test
    void controlCharactersAreEscapedAsUnicode() {
        assertThat(MustacheTemplates.escape("a\u0001b")).isEqualTo("a\\u0001b");
    }
}
