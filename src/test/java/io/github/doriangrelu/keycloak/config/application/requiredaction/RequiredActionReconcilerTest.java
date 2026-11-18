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

package io.github.doriangrelu.keycloak.config.application.requiredaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.application.requiredaction.port.RequiredActionGateway;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequiredActionReconcilerTest {

    /** Required actions in memory; logs every write. */
    static final class InMemoryRequiredActions implements RequiredActionGateway {
        final Map<String, RequiredAction> actions = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();

        @Override
        public List<RequiredAction> findAll(String realm) {
            return List.copyOf(this.actions.values());
        }

        @Override
        public void create(String realm, RequiredAction requiredAction) {
            this.log.add("create " + requiredAction.alias());
            this.actions.put(requiredAction.alias(), requiredAction);
        }

        @Override
        public void update(String realm, String currentAlias, RequiredAction requiredAction) {
            this.log.add("update " + currentAlias);
            this.actions.remove(currentAlias);
            this.actions.put(requiredAction.alias(), requiredAction);
        }

        @Override
        public void delete(String realm, String alias) {
            this.log.add("delete " + alias);
            this.actions.remove(alias);
        }
    }

    private InMemoryRequiredActions keycloak;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        this.keycloak = new InMemoryRequiredActions();
        this.engine = new ReconciliationEngine(List.of(new RequiredActionReconciler(this.keycloak)));
    }

    private static RequiredAction action(String alias, String providerId, boolean enabled, Map<String, String> config) {
        return new RequiredAction(alias, alias + " name", providerId, enabled, false, 0, config, null);
    }

    @Test
    void createsUpdatesAndPrunes() {
        this.keycloak.actions.put("CONFIGURE_TOTP", action("CONFIGURE_TOTP", "CONFIGURE_TOTP", false, null));
        this.keycloak.actions.put("orphan", action("orphan", "orphan", true, null));
        RealmContext context = new RealmContext("test", false, false, Map.of());

        this.engine.apply(
                context,
                new DesiredState()
                        .declare(
                                ResourceType.REQUIRED_ACTION,
                                List.of(
                                        action("CONFIGURE_TOTP", "CONFIGURE_TOTP", true, null),
                                        action("terms", "TERMS_AND_CONDITIONS", true, null))));
        this.engine.prune(context);

        assertThat(this.keycloak.log).containsExactly("update CONFIGURE_TOTP", "create terms", "delete orphan");
    }

    @Test
    void omittedConfigIsKept() {
        Map<String, String> config = Map.of("max_auth_age", "300");
        this.keycloak.actions.put("UPDATE_PASSWORD", action("UPDATE_PASSWORD", "UPDATE_PASSWORD", true, config));

        this.engine.apply(
                new RealmContext("test", false, false, Map.of()),
                new DesiredState()
                        .declare(
                                ResourceType.REQUIRED_ACTION,
                                List.of(action("UPDATE_PASSWORD", "UPDATE_PASSWORD", true, null))));

        assertThat(this.keycloak.log).isEmpty();
    }

    @Test
    void aNewProviderRecreatesTheRequiredAction() {
        this.keycloak.actions.put("custom", action("custom", "OLD_PROVIDER", true, null));

        this.engine.apply(
                new RealmContext("test", false, false, Map.of()),
                new DesiredState()
                        .declare(ResourceType.REQUIRED_ACTION, List.of(action("custom", "NEW_PROVIDER", true, null))));

        assertThat(this.keycloak.log).containsExactly("delete custom", "create custom");
        assertThat(this.keycloak.actions.get("custom").providerId()).isEqualTo("NEW_PROVIDER");
    }
}
