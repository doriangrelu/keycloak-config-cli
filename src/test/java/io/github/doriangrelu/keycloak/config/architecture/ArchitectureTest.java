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

package io.github.doriangrelu.keycloak.config.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Layering rules of ADR-001.
 *
 * <p>The layers are the {@code domain}, {@code application} and {@code infrastructure} packages. The other packages
 * hold what is not migrated yet: the Keycloak client ({@code provider}), the configuration ({@code properties}) and the
 * orchestration of an import ({@code service}). No package of the code base is part of a
 * dependency cycle.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "io.github.doriangrelu.keycloak.config";

    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String INFRASTRUCTURE = ROOT + ".infrastructure..";
    private static final String INFRASTRUCTURE_CLI = ROOT + ".infrastructure.cli..";
    private static final String INFRASTRUCTURE_SOURCE = ROOT + ".infrastructure.source..";
    private static final String INFRASTRUCTURE_KEYCLOAK = ROOT + ".infrastructure.keycloak..";

    @ArchTest
    static final ArchRule dependenciesPointInwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy(DOMAIN)
            .layer("Application")
            .definedBy(APPLICATION)
            .layer("Infrastructure")
            .definedBy(INFRASTRUCTURE)
            .whereLayer("Infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(DOMAIN, "java..");

    @ArchTest
    static final ArchRule applicationOnlyDependsOnDomain = classes()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(APPLICATION, DOMAIN, "java..", "org.springframework.stereotype..");

    @ArchTest
    static final ArchRule keycloakSdkStaysInKeycloakInfrastructure = noClasses()
            .that()
            .resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE_CLI, INFRASTRUCTURE_SOURCE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.keycloak..");

    @ArchTest
    static final ArchRule keycloakInfrastructureIgnoresSources = noClasses()
            .that()
            .resideInAPackage(INFRASTRUCTURE_KEYCLOAK)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(INFRASTRUCTURE_SOURCE);

    @ArchTest
    static final ArchRule sourcesIgnoreKeycloakInfrastructure = noClasses()
            .that()
            .resideInAPackage(INFRASTRUCTURE_SOURCE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(INFRASTRUCTURE_KEYCLOAK);

    @ArchTest
    static final ArchRule packagesAreFreeOfCycles =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule newCodeIsFreeOfCycles = slices().matching(ROOT + ".(domain|application|infrastructure).(*)..")
            .should()
            .beFreeOfCycles();
}
