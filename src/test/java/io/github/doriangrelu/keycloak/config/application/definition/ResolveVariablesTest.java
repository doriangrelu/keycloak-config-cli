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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResolveVariablesTest {

    private static final RealmDefinition DEMO =
            new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), List.of());

    private final FakeRealmDefinitions definitions = new FakeRealmDefinitions(DEMO);
    private final Map<String, String> environment = new HashMap<>();
    private final VariableValues remembered = new VariableValues();

    private ResolveVariables resolver(final Questions questions) {
        return new ResolveVariables(
                new ListVariables(this.definitions),
                name -> Optional.ofNullable(this.environment.get(name)),
                questions);
    }

    private void realmUses(final String... lines) {
        this.definitions.sources = Map.of("00-realm.yaml", String.join("\n", lines));
    }

    @Test
    void givenValueComesFirst() {
        realmUses("url: {{shopUrl:http://default}}");
        this.remembered.remember("shopUrl", "from-memory");
        this.environment.put("KCC_VAR_shopUrl", "from-env");

        Map<String, String> values =
                resolver(new ScriptedQuestions()).resolve("demo", Map.of("shopUrl", "from-option"), this.remembered);

        assertThat(values).containsExactly(Map.entry("shopUrl", "from-option"));
    }

    @Test
    void rememberedValueBeatsTheEnvironment() {
        realmUses("url: {{shopUrl:http://default}}");
        this.remembered.remember("shopUrl", "from-memory");
        this.environment.put("KCC_VAR_shopUrl", "from-env");

        Map<String, String> values = resolver(new ScriptedQuestions()).resolve("demo", Map.of(), this.remembered);

        assertThat(values).containsExactly(Map.entry("shopUrl", "from-memory"));
    }

    @Test
    void environmentBeatsTheDefault() {
        realmUses("url: {{shopUrl:http://default}}");
        this.environment.put("KCC_VAR_shopUrl", "from-env");

        Map<String, String> values = resolver(new ScriptedQuestions()).resolve("demo", Map.of(), this.remembered);

        assertThat(values).containsExactly(Map.entry("shopUrl", "from-env"));
    }

    @Test
    void aDefaultValueIsNotAsked() {
        realmUses("url: {{shopUrl:http://default}}", "other: {{shopUrl:http://other}}");
        ScriptedQuestions questions = new ScriptedQuestions();

        Map<String, String> values = resolver(questions).resolve("demo", Map.of(), this.remembered);

        assertThat(values).isEmpty();
        assertThat(questions.asked()).isEmpty();
    }

    @Test
    void aMissingValueIsAsked() {
        realmUses("url: {{shopUrl}}");
        ScriptedQuestions questions = new ScriptedQuestions("https://shop.example.org");

        Map<String, String> values = resolver(questions).resolve("demo", Map.of(), this.remembered);

        assertThat(values).containsExactly(Map.entry("shopUrl", "https://shop.example.org"));
        assertThat(questions.asked()).containsExactly("Value of 'shopUrl'");
    }

    @Test
    void aSecretNameIsAskedWithoutEcho() {
        realmUses("a: {{shopSecret}}", "b: {{SMTP_PASSWORD}}", "c: {{shopUrl}}");
        Questions questions = mock(Questions.class);
        when(questions.askSecret("Value of 'shopSecret'")).thenReturn("one");
        when(questions.askSecret("Value of 'SMTP_PASSWORD'")).thenReturn("two");
        when(questions.ask("Value of 'shopUrl'", Optional.empty())).thenReturn("three");

        Map<String, String> values = resolver(questions).resolve("demo", Map.of(), this.remembered);

        assertThat(values)
                .containsEntry("shopSecret", "one")
                .containsEntry("SMTP_PASSWORD", "two")
                .containsEntry("shopUrl", "three");
        verify(questions, never()).ask("Value of 'shopSecret'", Optional.empty());
        verify(questions, never()).ask("Value of 'SMTP_PASSWORD'", Optional.empty());
        verify(questions, never()).askSecret("Value of 'shopUrl'");
    }

    @Test
    void answersAndGivenValuesAreRemembered() {
        realmUses("a: {{shopUrl}}", "b: {{shopName}}");

        resolver(new ScriptedQuestions("https://shop.example.org"))
                .resolve("demo", Map.of("shopName", "Shop"), this.remembered);

        assertThat(this.remembered.get("shopUrl")).contains("https://shop.example.org");
        assertThat(this.remembered.get("shopName")).contains("Shop");
    }

    @Test
    void noAnswerInScriptModeFailsWithCode2() {
        realmUses("url: {{shopUrl}}");

        assertThatThrownBy(() -> resolver(new ScriptedQuestions()).resolve("demo", Map.of(), this.remembered))
                .isInstanceOf(MissingVariableException.class)
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Variable 'shopUrl' has no value: give it with --var shopUrl=… or KCC_VAR_shopUrl");
    }

    @Test
    void theMessageNeverHoldsAValue() {
        realmUses("a: {{shopSecret}}", "b: {{shopUrl}}");
        this.environment.put("KCC_VAR_shopSecret", "marker-s3cr3t");

        assertThatThrownBy(() -> resolver(new ScriptedQuestions()).resolve("demo", Map.of(), this.remembered))
                .isInstanceOf(MissingVariableException.class)
                .hasMessageNotContaining("marker-s3cr3t");
    }

    @Test
    void nothingIsAskedWhenEveryVariableHasAValue() {
        realmUses("a: {{shopUrl}}");
        Questions questions = mock(Questions.class);

        resolver(questions).resolve("demo", Map.of("shopUrl", "x"), this.remembered);

        verify(questions, never()).ask(any(), any());
    }

    @Test
    void parseVarsAcceptsAnEqualSignInTheValue() {
        assertThat(ResolveVariables.parseVars(List.of("token=a=b==", "empty=")))
                .containsEntry("token", "a=b==")
                .containsEntry("empty", "");
    }

    @Test
    void parseVarsRefusesAnOptionWithoutEqualSign() {
        assertThatThrownBy(() -> ResolveVariables.parseVars(List.of("shopSecret:marker-s3cr3t")))
                .isInstanceOf(InvalidVariableOptionException.class)
                .hasMessage("Invalid --var: expected name=value");
        assertThatThrownBy(() -> ResolveVariables.parseVars(List.of("=marker-s3cr3t")))
                .isInstanceOf(InvalidVariableOptionException.class)
                .hasMessageNotContaining("marker-s3cr3t");
    }

    @Test
    void aVariableWithADefaultInOnlyOnePlaceIsAskedLikeOneWithout() {
        realmUses("a: {{shopUrl:http://default}}", "b: {{shopUrl}}");

        Map<String, String> values =
                resolver(new ScriptedQuestions("https://shop")).resolve("demo", Map.of(), this.remembered);

        assertThat(values).containsExactly(Map.entry("shopUrl", "https://shop"));
        assertThat(this.remembered.get("shopUrl")).contains("https://shop");
    }

    @Test
    void aVariableWithADefaultInOnlyOnePlaceIsMissingInScriptMode() {
        realmUses("a: {{shopUrl:http://default}}", "b: {{shopUrl}}");

        assertThatThrownBy(() -> resolver(new ScriptedQuestions()).resolve("demo", Map.of(), this.remembered))
                .isInstanceOf(MissingVariableException.class)
                .hasMessage("Variable 'shopUrl' has no value: give it with --var shopUrl=… or KCC_VAR_shopUrl");
    }

    @Test
    void aVariableWithADefaultInOnlyOnePlaceTakesTheEnvironment() {
        realmUses("a: {{shopUrl:http://default}}", "b: {{shopUrl}}");
        this.environment.put("KCC_VAR_shopUrl", "from-env");

        Map<String, String> values = resolver(new ScriptedQuestions()).resolve("demo", Map.of(), this.remembered);

        assertThat(values).containsExactly(Map.entry("shopUrl", "from-env"));
    }

    @Test
    void resolveKnownNeverAsks() {
        realmUses("url: {{shopUrl}}", "port: {{port:8080}}", "ext: {{extra}}");
        this.remembered.remember("shopUrl", "from-memory");
        this.environment.put("KCC_VAR_extra", "from-env");

        Map<String, String> values = resolver(new ScriptedQuestions()).resolveKnown("demo", this.remembered);

        assertThat(values).containsOnly(Map.entry("shopUrl", "from-memory"), Map.entry("extra", "from-env"));
    }

    @Test
    void resolveKnownNamesTheMissingVariable() {
        realmUses("url: {{shopUrl}}");
        Questions questions = mock(Questions.class);

        assertThatThrownBy(() -> resolver(questions).resolveKnown("demo", this.remembered))
                .isInstanceOf(MissingVariableException.class)
                .hasMessageContaining("shopUrl");
        assertThat(this.remembered.get("shopUrl")).isEmpty();
        verify(questions, never()).ask(any(), any());
    }

    @Test
    void resolveKnownWithAnElementReadsTheSourcesWithIt() {
        realmUses("realm: demo");
        this.definitions.sourcesWith = Map.of("00-realm.yaml", "realm: demo", "20-clients.yaml", "id: {{appId:dev}}");
        this.environment.put("KCC_VAR_appId", "prod");
        ElementDefinition element =
                new GroupDefinition("/{{appId:dev}}", TemplatedValue.of("{{appId:dev}}"), Set.of(), Map.of());

        Map<String, String> values = resolver(new ScriptedQuestions()).resolveKnown("demo", element, this.remembered);

        assertThat(values).containsOnly(Map.entry("appId", "prod"));
    }
}
