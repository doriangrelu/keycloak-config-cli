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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.KeycloakConfigApplication;
import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.reconcile.ReconciliationEngine;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ImportCommandConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ImportRunner;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakClientFactory;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The wiring of a target is its own: the main context of the command-line import is not touched by it. */
class MainContextWiringTest {

    @Test
    void theMainContextOfTheImportNeverPicksUpTheWiringOfATarget() {
        new ApplicationContextRunner()
                .withUserConfiguration(KeycloakConfigApplication.class)
                .withPropertyValues(
                        "run.operation=IMPORT",
                        "keycloak.url=http://localhost:1",
                        "keycloak.version=26.5.3",
                        "keycloak.skip-server-info=true",
                        "keycloak.availability-check.enabled=false",
                        "import.files.locations=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanNamesForType(TargetConnectionConfiguration.class))
                            .isEmpty();
                    assertThat(context.getBeanNamesForType(KeycloakProvider.class))
                            .containsExactly("keycloakProvider");
                    assertThat(context.getBeanNamesForType(KeycloakClientFactory.class))
                            .containsExactly("keycloakClientFactory");
                });
    }

    @Test
    void theMainImportBeansAreUnchanged() {
        new ApplicationContextRunner()
                .withUserConfiguration(KeycloakConfigApplication.class)
                .withPropertyValues(
                        "run.operation=IMPORT",
                        "keycloak.url=http://localhost:1",
                        "keycloak.version=26.5.3",
                        "keycloak.skip-server-info=true",
                        "keycloak.availability-check.enabled=false",
                        "import.files.locations=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanNamesForType(ImportRealms.class)).containsExactly("importRealms");
                    String factory = context.getBeanFactory()
                            .getBeanDefinition("importRealms")
                            .getFactoryBeanName();
                    assertThat(context.getType(factory)).isEqualTo(ImportCommandConfiguration.class);
                    assertThat(context.getBeanNamesForType(KeycloakImportOptions.class))
                            .containsExactly("keycloakImportOptions");
                    assertThat(context.getBeanNamesForType(ReconciliationEngine.class))
                            .containsExactly("reconciliationEngine");
                    assertThat(context.getBeanNamesForType(ImportRunner.class)).hasSize(1);
                });
    }
}
