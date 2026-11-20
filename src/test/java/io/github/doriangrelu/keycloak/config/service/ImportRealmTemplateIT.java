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

package io.github.doriangrelu.keycloak.config.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.exception.InvalidImportException;
import java.io.IOException;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.test.context.TestPropertySource;

/** Import files rendered as Mustache templates (ADR-007). */
@TestPropertySource(
        properties = {
            "import.mustache.enabled=true",
            "import.mustache.variables.displayName=He said \"hi\" \\\\ R&D",
            "import.mustache.variables.verifyEmail=true",
            "import.mustache.variables.browserSecurityHeaders={\"xRobotsTag\":\"noindex\"}"
        })
class ImportRealmTemplateIT extends AbstractImportIT {
    private static final String REALM_NAME = "realm-template";
    private static final String DISPLAY_NAME = "He said \"hi\" \\ R&D";

    ImportRealmTemplateIT() {
        this.resourcePath = "import-files/realm-template";
    }

    @Test
    @Order(0)
    void shouldRenderVariablesDefaultValuesAndRawFragments() throws IOException {
        doImport("0_create_realm.json");

        RealmRepresentation realm =
                keycloakProvider.getInstance().realm(REALM_NAME).toRepresentation();

        // Quotes and backslashes keep their meaning, and HTML is not escaped
        assertThat(realm.getDisplayName(), is(DISPLAY_NAME));
        // Default values, the ${...} of Keycloak left untouched
        assertThat(realm.getDisplayNameHtml(), is("<b>R&D</b> - ${role_uma_authorization}"));
        assertThat(realm.getNotBefore(), is(600));
        assertThat(realm.isVerifyEmail(), is(true));
        // A triple mustache inserts a JSON fragment as it is
        assertThat(realm.getBrowserSecurityHeaders().get("xRobotsTag"), is("noindex"));
    }

    @Test
    @Order(1)
    void shouldRenderYamlFiles() throws IOException {
        doImport("1_update_realm.yaml");

        RealmRepresentation realm =
                keycloakProvider.getInstance().realm(REALM_NAME).toRepresentation();

        assertThat(realm.getDisplayName(), is(DISPLAY_NAME + " (yaml)"));
        assertThat(realm.getDisplayNameHtml(), is("<i>yaml</i>"));
        assertThat(realm.getNotBefore(), is(300));
        assertThat(realm.isVerifyEmail(), is(false));
    }

    @Test
    @Order(2)
    void shouldFailOnAnUndefinedVariableWithoutDefault() {
        InvalidImportException thrown =
                assertThrows(InvalidImportException.class, () -> doImport("2_missing_variable.json"));

        assertThat(thrown.getMessage(), containsString("'undefinedDisplayName'"));
        assertThat(thrown.getMessage(), containsString("2_missing_variable.json"));
        assertThat(
                keycloakProvider
                        .getInstance()
                        .realm(REALM_NAME)
                        .toRepresentation()
                        .getDisplayName(),
                is(DISPLAY_NAME + " (yaml)"));
    }
}
