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

package io.github.doriangrelu.keycloak.config.application.messagebundle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.messagebundle.port.MessageBundleGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageBundleReconcilerTest {

    /** Localization texts in memory. */
    static final class InMemoryMessageBundles implements MessageBundleGateway {
        final Map<String, Map<String, String>> locales = new LinkedHashMap<>();

        @Override
        public List<MessageBundle> findAll(String realm) {
            return this.locales.entrySet().stream()
                    .map(entry -> new MessageBundle(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public void saveTexts(String realm, String locale, Map<String, String> texts) {
            this.locales
                    .computeIfAbsent(locale, unused -> new LinkedHashMap<>())
                    .putAll(texts);
        }

        @Override
        public void deleteTexts(String realm, String locale, Set<String> keys) {
            this.locales.get(locale).keySet().removeAll(keys);
        }

        @Override
        public void deleteLocale(String realm, String locale) {
            this.locales.remove(locale);
        }
    }

    private InMemoryMessageBundles keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryMessageBundles();
        this.keycloak.locales.put("en", new LinkedHashMap<>(Map.of("kept", "old", "removed", "x")));
        this.keycloak.locales.put("de", new LinkedHashMap<>(Map.of("hallo", "Welt")));
        this.engine = new ReconciliationEngine(List.of(new MessageBundleReconciler(this.keycloak)));
    }

    private static DesiredState english(Map<String, String> texts) {
        return new DesiredState().declare(ResourceType.MESSAGE_BUNDLE, List.of(new MessageBundle("en", texts)));
    }

    @Test
    void replacesTheTextsOfADeclaredLocale() {
        this.engine.apply(context(), english(Map.of("kept", "new", "added", "y")));

        assertThat(this.keycloak.locales.get("en"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("kept", "new", "added", "y"));
    }

    @Test
    void removesTheLocalesNoLongerDeclared() {
        RealmContext context = context();

        this.engine.apply(context, english(Map.of("kept", "old")));
        this.engine.prune(context);

        assertThat(this.keycloak.locales).containsOnlyKeys("en");
    }

    private static RealmContext context() {
        return new RealmContext("test");
    }
}
