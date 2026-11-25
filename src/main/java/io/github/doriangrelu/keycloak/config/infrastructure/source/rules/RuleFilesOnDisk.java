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

package io.github.doriangrelu.keycloak.config.infrastructure.source.rules;

import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The base rules, embedded in the tool, and the rules file of the organisation, read from disk. */
public final class RuleFilesOnDisk implements RuleFiles {

    private static final String BASE_RESOURCE = "rules/base-rules.yaml";

    private final Optional<Path> organisationFile;

    public RuleFilesOnDisk(final Optional<Path> organisationFile) {
        this.organisationFile = organisationFile;
    }

    @Override
    public List<Rule> base() {
        try (InputStream stream = RuleFilesOnDisk.class.getClassLoader().getResourceAsStream(BASE_RESOURCE)) {
            if (stream == null) {
                throw new InvalidRulesException(BASE_RESOURCE + ": the built-in rules are missing");
            }
            return RuleFileReader.rules(BASE_RESOURCE, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidRulesException(BASE_RESOURCE + ": cannot be read: " + e.getMessage());
        }
    }

    @Override
    public OrganisationRules organisation() {
        if (this.organisationFile.isEmpty()) {
            return OrganisationRules.none();
        }
        Path file = this.organisationFile.get();
        String name = file.toString();
        if (!Files.isRegularFile(file)) {
            throw new InvalidRulesException(name + ": the rules file does not exist");
        }
        try {
            return RuleFileReader.organisation(name, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidRulesException(name + ": cannot be read: " + e.getMessage());
        }
    }
}
