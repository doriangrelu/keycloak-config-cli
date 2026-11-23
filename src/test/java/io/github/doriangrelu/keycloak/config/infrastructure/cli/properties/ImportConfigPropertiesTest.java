/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
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

package io.github.doriangrelu.keycloak.config.infrastructure.cli.properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import io.github.doriangrelu.keycloak.config.extensions.GithubActionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

// From: https://tuhrig.de/testing-configurationproperties-in-spring-boot/
@ExtendWith(SpringExtension.class)
@ExtendWith(GithubActionsExtension.class)
@SpringBootTest(classes = {ImportConfigPropertiesTest.TestConfiguration.class})
@TestPropertySource(
        properties = {
            "spring.main.log-startup-info=false",
            "import.validate=false",
            "import.files.locations=other",
            "import.files.include-hidden-files=true",
            "import.files.excludes=exclude1,exclude2",
            "import.behaviors.sync-user-federation=true",
            "import.behaviors.remove-default-role-from-user=true",
            "import.behaviors.skip-attributes-for-federated-user=true",
        })
class ImportConfigPropertiesTest {

    @Autowired
    private ImportConfigProperties properties;

    @Test
    @SuppressWarnings({"java:S2699", "java:S5961"})
    void shouldPopulateConfigurationProperties() {
        assertThat(properties.isValidate(), is(false));
        assertThat(properties.getFiles().getLocations(), contains("other"));
        assertThat(properties.getFiles().getExcludes(), contains("exclude1", "exclude2"));
        assertThat(properties.getFiles().isIncludeHiddenFiles(), is(true));
        assertThat(properties.getBehaviors().isSyncUserFederation(), is(true));
        assertThat(properties.getBehaviors().isRemoveDefaultRoleFromUser(), is(true));
        assertThat(properties.getBehaviors().isSkipAttributesForFederatedUser(), is(true));
    }

    @EnableConfigurationProperties(ImportConfigProperties.class)
    public static class TestConfiguration {
        // nothing
    }
}
