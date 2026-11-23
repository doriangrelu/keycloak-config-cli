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

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

import static io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.NormalizationConfigProperties.OutputFormat.YAML;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.properties.NormalizationConfigProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.NormalizedRealmFiles;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.RealmExportNormalizer;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.RealmExportParser;
import io.github.doriangrelu.keycloak.config.infrastructure.source.ExportFileReader;
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
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
public class NormalizationRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(NormalizationRunner.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final RealmExportNormalizer normalizer;
    private final ExportFileReader exportFileReader;
    private final RealmExportParser realmExportParser;
    private final NormalizedRealmFiles normalizedRealmFiles;
    private final NormalizationConfigProperties normalizationConfigProperties;
    private int exitCode;

    @Autowired
    public NormalizationRunner(
            RealmExportNormalizer normalizer,
            ExportFileReader exportFileReader,
            RealmExportParser realmExportParser,
            NormalizedRealmFiles normalizedRealmFiles,
            NormalizationConfigProperties normalizationConfigProperties) {
        this.normalizer = normalizer;
        this.exportFileReader = exportFileReader;
        this.realmExportParser = realmExportParser;
        this.normalizedRealmFiles = normalizedRealmFiles;
        this.normalizationConfigProperties = normalizationConfigProperties;
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

            for (ImportFile export : exportFileReader.read()) {
                logger.info("Normalizing file '{}'", export.name());
                for (var realm : realmExportParser.parse(export)) {
                    normalizedRealmFiles.write(
                            normalizer.normalize(realm),
                            outputLocation,
                            normalizationConfigProperties.getOutputFormat() == YAML);
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
