/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2022 adorsys GmbH & Co. KG @ https://adorsys.com
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

import static io.github.doriangrelu.keycloak.config.properties.NormalizationConfigProperties.OutputFormat.YAML;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCode;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.FailureReporter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.RealmExportNormalizer;
import io.github.doriangrelu.keycloak.config.properties.NormalizationConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.NormalizationKeycloakConfigProperties;
import io.github.doriangrelu.keycloak.config.provider.KeycloakExportProvider;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
@EnableConfigurationProperties({NormalizationConfigProperties.class, NormalizationKeycloakConfigProperties.class})
public class KeycloakConfigNormalizationRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfigNormalizationRunner.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final RealmExportNormalizer normalizer;
    private final KeycloakExportProvider exportProvider;
    private final NormalizationConfigProperties normalizationConfigProperties;
    private final YAMLMapper yamlMapper;
    private final ObjectMapper objectMapper;
    private int exitCode;

    @Autowired
    public KeycloakConfigNormalizationRunner(
            RealmExportNormalizer normalizer,
            KeycloakExportProvider exportProvider,
            NormalizationConfigProperties normalizationConfigProperties,
            YAMLMapper yamlMapper,
            ObjectMapper objectMapper) {
        this.normalizer = normalizer;
        this.exportProvider = exportProvider;
        this.normalizationConfigProperties = normalizationConfigProperties;
        this.yamlMapper = yamlMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        try {
            var outputLocation =
                    Paths.get(normalizationConfigProperties.getFiles().getOutputDirectory());
            if (!Files.exists(outputLocation)) {
                logger.info("Creating output directory '{}'", outputLocation);
                Files.createDirectories(outputLocation);
            }
            if (!Files.isDirectory(outputLocation)) {
                logger.error("Output location '{}' is not a directory. Aborting", outputLocation);
                exitCode = ExitCode.INVALID_CONFIGURATION.code();
                return;
            }

            for (var exportLocations : exportProvider.readFromLocations().values()) {
                for (var export : exportLocations.entrySet()) {
                    logger.info("Normalizing file '{}'", export.getKey());
                    for (var realm : export.getValue()) {
                        var normalizedRealm = normalizer.normalize(realm);
                        var suffix = normalizationConfigProperties.getOutputFormat() == YAML ? "yaml" : "json";
                        var outputFile =
                                outputLocation.resolve(String.format("%s.%s", normalizedRealm.getRealm(), suffix));
                        try (var os = new FileOutputStream(outputFile.toFile())) {
                            if (normalizationConfigProperties.getOutputFormat() == YAML) {
                                yamlMapper.writeValue(os, normalizedRealm);
                            } else {
                                objectMapper.writeValue(os, normalizedRealm);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Single place where normalization failures are caught and turned into an exit code (ADR-005)
            exitCode = FailureReporter.report(logger, "normalization", e).code();
        } finally {
            long totalTime = System.currentTimeMillis() - START_TIME;
            String formattedTime = new SimpleDateFormat("mm:ss.SSS").format(new Date(totalTime));
            logger.info("keycloak-config-cli running in {}.", formattedTime);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
