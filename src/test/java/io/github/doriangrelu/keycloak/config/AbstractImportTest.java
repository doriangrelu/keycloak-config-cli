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

package io.github.doriangrelu.keycloak.config;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.github.doriangrelu.keycloak.config.configuration.TestConfiguration;
import io.github.doriangrelu.keycloak.config.extensions.GithubActionsExtension;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.provider.KeycloakImportProvider;
import io.github.doriangrelu.keycloak.config.service.RealmImportService;
import io.github.doriangrelu.keycloak.config.service.state.ExecutionContextHolder;
import io.github.doriangrelu.keycloak.config.test.util.KeycloakAuthentication;
import io.github.doriangrelu.keycloak.config.test.util.KeycloakRepository;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ExtendWith(GithubActionsExtension.class)
@ContextConfiguration(
        classes = {TestConfiguration.class},
        initializers = {ConfigDataApplicationContextInitializer.class})
@ActiveProfiles("IT")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Timeout(value = 30, unit = SECONDS)
public abstract class AbstractImportTest {
    @Autowired
    public RealmImportService realmImportService;

    @Autowired
    public KeycloakImportProvider keycloakImportProvider;

    @Autowired
    public KeycloakRepository keycloakRepository;

    @Autowired
    public KeycloakAuthentication keycloakAuthentication;

    public String resourcePath;

    public void doImport(String fileName) throws IOException {
        doImport(fileName, realmImportService);
    }

    public void doImport(String fileName, RealmImportService _realmImportService) throws IOException {
        runImport(getImport(fileName), _realmImportService);
    }

    /**
     * Imports like one run of the command line does (see {@code KeycloakConfigRunner}): fresh execution context,
     * import of every part, then the clean phase that deletes resources missing from the import.
     */
    public void runImport(List<RealmImport> realmImports) {
        runImport(realmImports, realmImportService);
    }

    public void runImport(List<RealmImport> realmImports, RealmImportService _realmImportService) {
        ExecutionContextHolder.initializeEmptyContext();
        realmImports.forEach(_realmImportService::doImport);
        realmImports.forEach(_realmImportService::cleanRealm);
    }

    public RealmImport getFirstImport(String fileName) throws IOException {
        return getImport(fileName).get(0);
    }

    public List<RealmImport> getImport(String fileName) {
        String location = "classpath:" + this.resourcePath + '/' + fileName;
        return keycloakImportProvider.readFromLocations(location).getRealmImports().get(location).entrySet().stream()
                .findFirst()
                .orElseThrow()
                .getValue();
    }
}
