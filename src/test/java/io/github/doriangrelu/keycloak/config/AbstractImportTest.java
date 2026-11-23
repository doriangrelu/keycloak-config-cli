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

import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.RealmDeclaration;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import io.github.doriangrelu.keycloak.config.configuration.TestConfiguration;
import io.github.doriangrelu.keycloak.config.extensions.GithubActionsExtension;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImport;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImportDeclarations;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation.RealmImportParser;
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
    public ImportRealms importRealms;

    @Autowired
    public RealmImportDeclarations realmImportDeclarations;

    /** What the last import of the test changed. */
    public ImportReport lastImport;

    @Autowired
    public ImportFileSource importFileSource;

    @Autowired
    public RealmImportParser realmImportParser;

    @Autowired
    public KeycloakRepository keycloakRepository;

    @Autowired
    public KeycloakAuthentication keycloakAuthentication;

    public String resourcePath;

    public void doImport(String fileName) throws IOException {
        runImport(getImport(fileName));
    }

    /** Imports with the use case of another Spring context, such as the one of a nested test with other properties. */
    public void doImport(String fileName, ImportRealms _importRealms) throws IOException {
        lastImport = _importRealms.importRealms(declarationsOf(getImport(fileName)));
    }

    /**
     * Imports like one run of the command line does (see {@code KeycloakConfigRunner}): fresh execution context,
     * import of every part, then the clean phase that deletes resources missing from the import.
     */
    public void runImport(List<RealmImport> realmImports) {
        lastImport = importRealms.importRealms(declarationsOf(realmImports));
    }

    /** Imports without the clean phase: nothing is deleted. */
    public void runImportWithoutCleanup(RealmImport realmImport) {
        lastImport = importRealms.importRealmsWithoutCleanup(declarationsOf(List.of(realmImport)));
    }

    private List<RealmDeclaration> declarationsOf(List<RealmImport> realmImports) {
        return realmImports.stream().map(realmImportDeclarations::declarationOf).toList();
    }

    public RealmImport getFirstImport(String fileName) throws IOException {
        return getImport(fileName).get(0);
    }

    public List<RealmImport> getImport(String fileName) {
        String location = "classpath:" + this.resourcePath + '/' + fileName;
        return realmImportParser.parse(importFileSource.read(List.of(location)).getFirst());
    }
}
