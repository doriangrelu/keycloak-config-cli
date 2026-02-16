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
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Map;

@ConfigurationProperties(prefix = "import", ignoreUnknownFields = false)
@Validated
@SuppressWarnings({"java:S107"})
public class ImportConfigProperties {
    public static final String REALM_STATE_ATTRIBUTE_COMMON_PREFIX = "io.github.doriangrelu.keycloak.config";
    public static final String REALM_CHECKSUM_ATTRIBUTE_PREFIX_KEY = REALM_STATE_ATTRIBUTE_COMMON_PREFIX + ".import-checksum-{0}";
    public static final String REALM_STATE_ATTRIBUTE_PREFIX_KEY = REALM_STATE_ATTRIBUTE_COMMON_PREFIX + ".state-{0}-{1}";

    @NotNull
    private final boolean validate;

    @NotNull
    private final boolean parallel;

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

    @Valid
    private final ImportManagedProperties managed;

    @Valid
    private final ImportRemoteStateProperties remoteState;

    public ImportConfigProperties(@DefaultValue("true") final boolean validate,
                                  @DefaultValue("false") final boolean parallel,
                                  @DefaultValue final ImportFilesProperties files,
                                  @DefaultValue final ImportVarSubstitutionProperties varSubstitution,
                                  @DefaultValue final ImportMustacheProperties mustache,
                                  @DefaultValue final ImportBehaviorsProperties behaviors,
                                  @DefaultValue final ImportCacheProperties cache,
                                  @DefaultValue final ImportManagedProperties managed,
                                  @DefaultValue final ImportRemoteStateProperties remoteState
    ) {
        this.validate = validate;
        this.parallel = parallel;
        this.files = files;
        this.varSubstitution = varSubstitution;
        this.mustache = mustache;
        this.behaviors = behaviors;
        this.cache = cache;
        this.managed = managed;
        this.remoteState = remoteState;
    }

    public boolean isValidate() {
        return this.validate;
    }

    public boolean isParallel() {
        return this.parallel;
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

    public ImportManagedProperties getManaged() {
        return this.managed;
    }

    public ImportRemoteStateProperties getRemoteState() {
        return this.remoteState;
    }

    @SuppressWarnings("unused")
    public static class ImportManagedProperties {
        @NotNull
        private final ImportManagedPropertiesValues requiredAction;

        @NotNull
        private final ImportManagedPropertiesValues group;

        @NotNull
        private final ImportManagedPropertiesValues clientScope;

        @NotNull
        private final ImportManagedPropertiesValues scopeMapping;

        @NotNull
        private final ImportManagedPropertiesValues clientScopeMapping;

        @NotNull
        private final ImportManagedPropertiesValues component;

        @NotNull
        private final ImportManagedPropertiesValues subComponent;

        @NotNull
        private final ImportManagedPropertiesValues authenticationFlow;

        @NotNull
        private final ImportManagedPropertiesValues identityProvider;

        @NotNull
        private final ImportManagedPropertiesValues identityProviderMapper;

        @NotNull
        private final ImportManagedPropertiesValues role;

        @NotNull
        private final ImportManagedPropertiesValues client;

        @NotNull
        private final ImportManagedPropertiesValues clientAuthorizationResources;

        @NotNull
        private final ImportManagedPropertiesValues clientAuthorizationPolicies;

        @NotNull
        private final ImportManagedPropertiesValues clientAuthorizationScopes;

        @NotNull
        private final ImportManagedPropertiesValues messageBundles;

        public ImportManagedProperties(@DefaultValue("FULL") final ImportManagedPropertiesValues requiredAction,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues group,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues clientScope,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues scopeMapping,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues clientScopeMapping,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues component,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues subComponent,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues authenticationFlow,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues identityProvider,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues identityProviderMapper,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues role,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues client,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues clientAuthorizationResources,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues clientAuthorizationPolicies,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues clientAuthorizationScopes,
                                       @DefaultValue("FULL") final ImportManagedPropertiesValues messageBundles) {
            this.requiredAction = requiredAction;
            this.group = group;
            this.clientScope = clientScope;
            this.scopeMapping = scopeMapping;
            this.clientScopeMapping = clientScopeMapping;
            this.component = component;
            this.subComponent = subComponent;
            this.authenticationFlow = authenticationFlow;
            this.identityProvider = identityProvider;
            this.identityProviderMapper = identityProviderMapper;
            this.role = role;
            this.client = client;
            this.clientAuthorizationResources = clientAuthorizationResources;
            this.clientAuthorizationPolicies = clientAuthorizationPolicies;
            this.clientAuthorizationScopes = clientAuthorizationScopes;
            this.messageBundles = messageBundles;
        }

        public ImportManagedPropertiesValues getRequiredAction() {
            return this.requiredAction;
        }

        public ImportManagedPropertiesValues getClientScope() {
            return this.clientScope;
        }

        public ImportManagedPropertiesValues getScopeMapping() {
            return this.scopeMapping;
        }

        public ImportManagedPropertiesValues getClientScopeMapping() {
            return this.clientScopeMapping;
        }

        public ImportManagedPropertiesValues getComponent() {
            return this.component;
        }

        public ImportManagedPropertiesValues getSubComponent() {
            return this.subComponent;
        }

        public ImportManagedPropertiesValues getAuthenticationFlow() {
            return this.authenticationFlow;
        }

        public ImportManagedPropertiesValues getGroup() {
            return this.group;
        }

        public ImportManagedPropertiesValues getIdentityProvider() {
            return this.identityProvider;
        }

        public ImportManagedPropertiesValues getIdentityProviderMapper() {
            return this.identityProviderMapper;
        }

        public ImportManagedPropertiesValues getRole() {
            return this.role;
        }

        public ImportManagedPropertiesValues getClient() {
            return this.client;
        }

        public ImportManagedPropertiesValues getClientAuthorizationResources() {
            return this.clientAuthorizationResources;
        }

        public ImportManagedPropertiesValues getClientAuthorizationPolicies() {
            return this.clientAuthorizationPolicies;
        }

        public ImportManagedPropertiesValues getClientAuthorizationScopes() {
            return this.clientAuthorizationScopes;
        }

        public ImportManagedPropertiesValues getMessageBundles() {
            return this.messageBundles;
        }

        public enum ImportManagedPropertiesValues {
            FULL, NO_DELETE
        }
    }

    @SuppressWarnings("unused")
    public static class ImportFilesProperties {
        @NotNull
        private final Collection<String> locations;

        @NotNull
        private final Collection<String> excludes;

        @NotNull
        private final boolean includeHiddenFiles;

        public ImportFilesProperties(final Collection<String> locations,
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

        public ImportVarSubstitutionProperties(@DefaultValue("false") final boolean enabled,
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

        public ImportMustacheProperties(@DefaultValue("false") final boolean enabled,
                                        @DefaultValue final Map<String, String> variables) {
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

        public ImportBehaviorsProperties(final boolean syncUserFederation, final boolean removeDefaultRoleFromUser, final boolean skipAttributesForFederatedUser,
                                         final boolean checksumWithCacheKey, final ChecksumChangedOption checksumChanged) {
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
            CONTINUE, FAIL
        }
    }

    @SuppressWarnings("unused")
    public static class ImportCacheProperties {
        @NotNull
        private final boolean enabled;

        @NotNull
        private final String key;

        public ImportCacheProperties(@DefaultValue("true") final boolean enabled,
                                     @DefaultValue("default") final String key) {
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

    @SuppressWarnings("unused")
    public static class ImportRemoteStateProperties {
        @NotNull
        private final boolean enabled;

        private final String encryptionKey;

        @Pattern(regexp = "^[A-Fa-f0-9]+$")
        private final String encryptionSalt;

        public ImportRemoteStateProperties(@DefaultValue("true") final boolean enabled,
                                           final String encryptionKey,
                                           @DefaultValue("2B521C795FBE2F2425DB150CD3700BA9") final String encryptionSalt) {
            this.enabled = enabled;
            this.encryptionKey = encryptionKey;
            this.encryptionSalt = encryptionSalt;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public String getEncryptionKey() {
            return this.encryptionKey;
        }

        public String getEncryptionSalt() {
            return this.encryptionSalt;
        }
    }
}
