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

package io.github.doriangrelu.keycloak.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "import", ignoreUnknownFields = false)
@Validated
@SuppressWarnings({"java:S107"})
public class ImportConfigProperties {
    public static final String REALM_STATE_ATTRIBUTE_COMMON_PREFIX = "io.github.doriangrelu.keycloak.config";
    public static final String REALM_CHECKSUM_ATTRIBUTE_PREFIX =
            REALM_STATE_ATTRIBUTE_COMMON_PREFIX + ".import-checksum-";
    public static final String REALM_CHECKSUM_ATTRIBUTE_PREFIX_KEY = REALM_CHECKSUM_ATTRIBUTE_PREFIX + "{0}";
    /** Prefix of the realm attributes of the former remote state, removed by any import (ADR-007). */
    public static final String REALM_STATE_ATTRIBUTE_OBSOLETE_PREFIX = REALM_STATE_ATTRIBUTE_COMMON_PREFIX + ".state-";

    @NotNull
    private final boolean validate;

    @NotNull
    private final boolean parallel;

    /** Computes and reports the changes of the import without applying them. */
    @NotNull
    private final boolean dryRun;

    @NotNull
    private final DryRunFormat dryRunFormat;

    /** File the dry run report is also written to, in its format; none by default. */
    private final String dryRunReportFile;

    @Valid
    private final ImportFilesProperties files;

    @Valid
    private final ImportVarSubstitutionProperties varSubstitution;

    @Valid
    private final ImportMustacheProperties mustache;

    @Valid
    private final ImportBehaviorsProperties behaviors;

    @Valid
    private final ImportCacheProperties cache;

    public ImportConfigProperties(
            @DefaultValue("true") final boolean validate,
            @DefaultValue("false") final boolean parallel,
            @DefaultValue("false") final boolean dryRun,
            @DefaultValue("TEXT") final DryRunFormat dryRunFormat,
            final String dryRunReportFile,
            @DefaultValue final ImportFilesProperties files,
            @DefaultValue final ImportVarSubstitutionProperties varSubstitution,
            @DefaultValue final ImportMustacheProperties mustache,
            @DefaultValue final ImportBehaviorsProperties behaviors,
            @DefaultValue final ImportCacheProperties cache) {
        this.validate = validate;
        this.parallel = parallel;
        this.dryRun = dryRun;
        this.dryRunFormat = dryRunFormat;
        this.dryRunReportFile = dryRunReportFile;
        this.files = files;
        this.varSubstitution = varSubstitution;
        this.mustache = mustache;
        this.behaviors = behaviors;
        this.cache = cache;
    }

    public boolean isValidate() {
        return this.validate;
    }

    public boolean isParallel() {
        return this.parallel;
    }

    public boolean isDryRun() {
        return this.dryRun;
    }

    public DryRunFormat getDryRunFormat() {
        return this.dryRunFormat;
    }

    public String getDryRunReportFile() {
        return this.dryRunReportFile;
    }

    /** Format of the dry run report. */
    public enum DryRunFormat {
        TEXT,
        JSON
    }

    public ImportFilesProperties getFiles() {
        return this.files;
    }

    public ImportVarSubstitutionProperties getVarSubstitution() {
        return this.varSubstitution;
    }

    public ImportMustacheProperties getMustache() {
        return this.mustache;
    }

    public ImportBehaviorsProperties getBehaviors() {
        return this.behaviors;
    }

    public ImportCacheProperties getCache() {
        return this.cache;
    }

    @SuppressWarnings("unused")
    public static class ImportFilesProperties {
        @NotNull
        private final Collection<String> locations;

        @NotNull
        private final Collection<String> excludes;

        @NotNull
        private final boolean includeHiddenFiles;

        public ImportFilesProperties(
                final Collection<String> locations,
                @DefaultValue final Collection<String> excludes,
                @DefaultValue("false") final boolean includeHiddenFiles) {
            this.locations = locations;
            this.excludes = excludes;
            this.includeHiddenFiles = includeHiddenFiles;
        }

        public Collection<String> getLocations() {
            return this.locations;
        }

        public Collection<String> getExcludes() {
            return this.excludes;
        }

        public boolean isIncludeHiddenFiles() {
            return this.includeHiddenFiles;
        }
    }

    @SuppressWarnings("unused")
    public static class ImportVarSubstitutionProperties {
        @NotNull
        private final boolean enabled;

        @NotNull
        private final boolean nested;

        @NotNull
        private final boolean undefinedIsError;

        @NotNull
        private final String prefix;

        @NotNull
        private final String suffix;

        public ImportVarSubstitutionProperties(
                @DefaultValue("false") final boolean enabled,
                @DefaultValue("true") final boolean nested,
                @DefaultValue("true") final boolean undefinedIsError,
                @DefaultValue("$(") final String prefix,
                @DefaultValue(")") final String suffix) {
            this.enabled = enabled;
            this.nested = nested;
            this.undefinedIsError = undefinedIsError;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public boolean isNested() {
            return this.nested;
        }

        public boolean isUndefinedIsError() {
            return this.undefinedIsError;
        }

        public String getPrefix() {
            return this.prefix;
        }

        public String getSuffix() {
            return this.suffix;
        }
    }

    @SuppressWarnings("unused")
    public static class ImportMustacheProperties {
        @NotNull
        private final boolean enabled;

        @NotNull
        private final Map<String, String> variables;

        public ImportMustacheProperties(
                @DefaultValue("false") final boolean enabled, @DefaultValue final Map<String, String> variables) {
            this.enabled = enabled;
            this.variables = variables;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public Map<String, String> getVariables() {
            return Map.copyOf(this.variables);
        }
    }

    @SuppressWarnings("unused")
    public static class ImportBehaviorsProperties {
        @NotNull
        private final boolean syncUserFederation;

        @NotNull
        private final boolean removeDefaultRoleFromUser;

        @NotNull
        private final boolean skipAttributesForFederatedUser;

        @NotNull
        private final boolean checksumWithCacheKey;

        @NotNull
        private final ChecksumChangedOption checksumChanged;

        public ImportBehaviorsProperties(
                final boolean syncUserFederation,
                final boolean removeDefaultRoleFromUser,
                final boolean skipAttributesForFederatedUser,
                final boolean checksumWithCacheKey,
                final ChecksumChangedOption checksumChanged) {
            this.syncUserFederation = syncUserFederation;
            this.removeDefaultRoleFromUser = removeDefaultRoleFromUser;
            this.skipAttributesForFederatedUser = skipAttributesForFederatedUser;
            this.checksumWithCacheKey = checksumWithCacheKey;
            this.checksumChanged = checksumChanged;
        }

        public boolean isSyncUserFederation() {
            return this.syncUserFederation;
        }

        public boolean isRemoveDefaultRoleFromUser() {
            return this.removeDefaultRoleFromUser;
        }

        public boolean isSkipAttributesForFederatedUser() {
            return this.skipAttributesForFederatedUser;
        }

        public boolean isChecksumWithCacheKey() {
            return this.checksumWithCacheKey;
        }

        public ChecksumChangedOption getChecksumChanged() {
            return this.checksumChanged;
        }

        public enum ChecksumChangedOption {
            CONTINUE,
            FAIL
        }
    }

    @SuppressWarnings("unused")
    public static class ImportCacheProperties {
        @NotNull
        private final boolean enabled;

        @NotNull
        private final String key;

        public ImportCacheProperties(
                @DefaultValue("true") final boolean enabled, @DefaultValue("default") final String key) {
            this.enabled = enabled;
            this.key = key;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public String getKey() {
            return this.key;
        }
    }
}
