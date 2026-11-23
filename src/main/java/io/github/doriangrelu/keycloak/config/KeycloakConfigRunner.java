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

import io.github.doriangrelu.keycloak.config.application.importation.ImportRealms;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.DryRunReport;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCode;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.FailureReporter;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.KeycloakConfigProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
/*
 * Spring only considers actual properties set, not default values of @ConfigurationProperties classes.
 * Therefore, we enable matchIfMissing here, so if there is *no* property set, we consider it an import
 * for backwards compatibility
 */
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
@EnableConfigurationProperties({ImportConfigProperties.class, KeycloakConfigProperties.class})
public class KeycloakConfigRunner implements CommandLineRunner, ExitCodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfigRunner.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final ImportRealms importRealms;
    private final ImportConfigProperties importConfigProperties;

    private int exitCode = 0;

    @Autowired
    public KeycloakConfigRunner(ImportRealms importRealms, ImportConfigProperties importConfigProperties) {
        this.importRealms = importRealms;
        this.importConfigProperties = importConfigProperties;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void run(String... args) {
        try {
            ImportReport report =
                    importRealms.importFiles(importConfigProperties.getFiles().getLocations());
            if (importConfigProperties.isDryRun()) {
                exitCode = reportDryRun(report).code();
            }
        } catch (Exception e) {
            // Single place where import failures are caught and turned into an exit code (ADR-005)
            exitCode = FailureReporter.report(logger, "import", e).code();
        } finally {
            long totalTime = System.currentTimeMillis() - START_TIME;
            String formattedTime = new SimpleDateFormat("mm:ss.SSS").format(new Date(totalTime));
            logger.info("keycloak-config-cli ran in {}.", formattedTime);
        }
    }

    /**
     * Shows what the import would change (EPIC-004): on the standard output, and in the report file when one is
     * configured. Pending changes give exit code {@code 10} (ADR-005).
     */
    private ExitCode reportDryRun(final ImportReport importReport) {
        DryRunReport report = DryRunReport.of(importReport.plans());
        String content = importConfigProperties.getDryRunFormat() == ImportConfigProperties.DryRunFormat.JSON
                ? report.toJson()
                : report.toText();
        System.out.println(content);
        String reportFile = importConfigProperties.getDryRunReportFile();
        if (reportFile != null && !reportFile.isBlank()) {
            try {
                Files.writeString(Path.of(reportFile), content, StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new UncheckedIOException("Cannot write the dry run report to " + reportFile, error);
            }
        }
        return report.pendingChanges() == 0 ? ExitCode.SUCCESS : ExitCode.PENDING_CHANGES;
    }
}
