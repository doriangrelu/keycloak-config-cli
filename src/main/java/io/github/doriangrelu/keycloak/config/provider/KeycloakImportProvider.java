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

package io.github.doriangrelu.keycloak.config.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samskivert.mustache.Mustache;
import io.github.doriangrelu.keycloak.config.exception.InvalidImportException;
import io.github.doriangrelu.keycloak.config.model.ImportResource;
import io.github.doriangrelu.keycloak.config.mustache.MustacheContextWithDefaults;
import io.github.doriangrelu.keycloak.config.model.KeycloakImport;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.PathMatcher;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class KeycloakImportProvider {
    protected static final String DEFAULT_VALUE = "";
    private final PathMatchingResourcePatternResolver patternResolver;
    private final ImportConfigProperties importConfigProperties;

    private StringSubstitutor interpolator = null;

    private static final Logger logger = LoggerFactory.getLogger(KeycloakImportProvider.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Autowired
    public KeycloakImportProvider(
            final Environment environment,
            final PathMatchingResourcePatternResolver patternResolver,
            final ImportConfigProperties importConfigProperties
    ) {
        this.patternResolver = patternResolver;
        this.importConfigProperties = importConfigProperties;

        if (importConfigProperties.getVarSubstitution().isEnabled()) {
            this.setupVariableSubstitution(environment);
        }
    }

    private void setupVariableSubstitution(final Environment environment) {
        final StringLookup variableResolver = StringLookupFactory.INSTANCE.interpolatorStringLookup(
                StringLookupFactory.INSTANCE.functionStringLookup(environment::getProperty)
        );

        this.interpolator = StringSubstitutor.createInterpolator()
                .setVariableResolver(variableResolver)
                .setVariablePrefix(this.importConfigProperties.getVarSubstitution().getPrefix())
                .setVariableSuffix(this.importConfigProperties.getVarSubstitution().getSuffix())
                .setEnableSubstitutionInVariables(this.importConfigProperties.getVarSubstitution().isNested())
                .setEnableUndefinedVariableException(this.importConfigProperties.getVarSubstitution().isUndefinedIsError());
    }

    public KeycloakImport readFromLocations(final String... locations) {
        return this.readFromLocations(Arrays.asList(locations));
    }

    public KeycloakImport readFromLocations(final Collection<String> locations) {
        final Map<String, Map<String, List<RealmImport>>> realmImports = new LinkedHashMap<>();

        for (final String location : locations) {
            logger.debug("Loading file location '{}'", location);
            final String resourceLocation = this.prepareResourceLocation(location);

            Resource[] resources;
            try {
                resources = this.patternResolver.getResources(resourceLocation);
            } catch (final IOException e) {
                throw new InvalidImportException("Unable to proceed location '" + location + "': " + e.getMessage(), e);
            }

            resources = Arrays.stream(resources).filter(this::filterExcludedResources).toArray(Resource[]::new);

            if (resources.length == 0) {
                throw new InvalidImportException("No files matching '" + location + "'!");
            }

            // Import Pipe
            final Map<String, List<RealmImport>> realmImport = Arrays.stream(resources)
                    .map(this::readResource)
                    .filter(this::filterEmptyResources)
                    .sorted(Map.Entry.comparingByKey())
                    .map(this::substituteImportResource)
                    .map(this::applyMustacheTemplate)
                    .map(this::readRealmImportFromImportResource)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));

            realmImports.put(location, realmImport);
        }

        return new KeycloakImport(realmImports);
    }

    private boolean filterExcludedResources(final Resource resource) {
        if (!resource.isFile()) {
            return true;
        }

        final File file;

        try {
            file = resource.getFile();
        } catch (final IOException ignored) {
            return true;
        }

        if (file.isDirectory()) {
            return false;
        }

        if (!this.importConfigProperties.getFiles().isIncludeHiddenFiles() && (file.isHidden() || FileUtils.hasHiddenAncestorDirectory(file))) {
            return false;
        }

        final PathMatcher pathMatcher = this.patternResolver.getPathMatcher();
        return this.importConfigProperties.getFiles().getExcludes()
                .stream()
                .map(pattern -> pattern.startsWith("**") ? "/" + pattern : pattern)
                .map(pattern -> !pattern.startsWith("/**") ? "/**" + pattern : pattern)
                .map(pattern -> !pattern.startsWith("/") ? "/" + pattern : pattern)
                .noneMatch(pattern -> {
                    final boolean match = pathMatcher.match(pattern, file.getPath());
                    if (match) {
                        logger.debug("Excluding resource file '{}' (match {})", file.getPath(), pattern);
                        return true;
                    }
                    return false;
                });
    }

    private ImportResource readResource(Resource resource) {
        logger.debug("Loading file '{}'", resource.getFilename());

        try {
            resource = this.setupAuthentication(resource);
            try (final InputStream inputStream = resource.getInputStream()) {
                return new ImportResource(resource.getURI().toString(), new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (final IOException e) {
            throw new InvalidImportException("Unable to proceed resource '" + resource + "': " + e.getMessage(), e);
        } finally {
            Authenticator.setDefault(null);
        }
    }

    private boolean filterEmptyResources(final ImportResource resource) {
        return !resource.getValue().isEmpty();
    }

    private ImportResource substituteImportResource(final ImportResource importResource) {
        if (this.importConfigProperties.getVarSubstitution().isEnabled()) {
            importResource.setValue(this.interpolator.replace(importResource.getValue()));
        }

        return importResource;
    }

    private ImportResource applyMustacheTemplate(final ImportResource importResource) {
        if (this.importConfigProperties.getMustache().isEnabled()) {
            final Map<String, String> variables = this.importConfigProperties.getMustache().getVariables();
            final String result = Mustache.compiler()
                    .defaultValue(DEFAULT_VALUE)
                    .compile(importResource.getValue())
                    .execute(new MustacheContextWithDefaults(variables));
            importResource.setValue(result);
        }

        return importResource;
    }

    private Pair<String, List<RealmImport>> readRealmImportFromImportResource(final ImportResource resource) {
        final String location = resource.getFilename();
        final String content = resource.getValue();
        final String contentChecksum = DigestUtils.sha256Hex(content);

        if (logger.isTraceEnabled()) {
            logger.trace(content);
        }

        final List<RealmImport> realmImports;
        try {
            realmImports = this.readContent(content);
        } catch (final Exception e) {
            throw new InvalidImportException("Unable to parse file '" + location + "': " + e.getMessage(), e);
        }
        realmImports.forEach(realmImport -> {
            realmImport.setChecksum(contentChecksum);
            realmImport.setSource(location);
        });

        return new ImmutablePair<>(location, realmImports);
    }

    private List<RealmImport> readContent(final String content) {
        final List<RealmImport> realmImports = new ArrayList<>();

        final Yaml yaml = new Yaml();
        final Iterable<Object> yamlDocuments = yaml.loadAll(content);

        for (final Object yamlDocument : yamlDocuments) {
            realmImports.add(OBJECT_MAPPER.convertValue(yamlDocument, RealmImport.class));
        }

        return realmImports;
    }

    private String prepareResourceLocation(final String location) {
        String importLocation = location;

        importLocation = importLocation.replaceFirst("^zip:", "jar:");

        // backward compatibility to correct a possible missing prefix "file:" in path
        if (!importLocation.contains(":")) {
            importLocation = "file:" + importLocation;
        }
        return importLocation;
    }

    private Resource setupAuthentication(final Resource resource) throws IOException {
        final String userInfo;

        try {
            userInfo = resource.getURL().getUserInfo();
        } catch (final IOException e) {
            return resource;
        }

        if (userInfo == null) {
            return resource;
        }

        final String[] userInfoSplit = userInfo.split(":");

        if (userInfoSplit.length != 2) {
            return resource;
        }

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userInfoSplit[0], userInfoSplit[1].toCharArray());
            }
        });

        // Mask AuthInfo
        final String location = resource.getURI().toString().replace(userInfo + "@", "***@");
        return new UrlResource(location);
    }
}
