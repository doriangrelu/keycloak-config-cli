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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.PathMatcher;

/**
 * Finds and reads the files of a list of locations: a file, a directory or Ant pattern, a regular expression in braces,
 * a zip archive ({@code zip:}), the class path or a remote URL, with basic authentication when the URL holds
 * credentials. Shared by the import and the normalization.
 *
 * <ul>
 *   <li>a location without a scheme is a local file ({@code file:});
 *   <li>directories are skipped, and so are hidden files unless {@code includeHiddenFiles};
 *   <li>files matching one of the {@code excludes} Ant patterns are skipped;
 *   <li>the files of a location are sorted by name, and empty files are skipped.
 * </ul>
 */
public final class FileLocations {

    private static final Logger logger = LoggerFactory.getLogger(FileLocations.class);

    private final PathMatchingResourcePatternResolver patternResolver;
    private final List<String> excludes;
    private final boolean includeHiddenFiles;

    public FileLocations(
            final PathMatchingResourcePatternResolver patternResolver,
            final Collection<String> excludes,
            final boolean includeHiddenFiles) {
        this.patternResolver = Objects.requireNonNull(patternResolver, "patternResolver");
        this.excludes = List.copyOf(excludes);
        this.includeHiddenFiles = includeHiddenFiles;
    }

    /**
     * @throws InvalidImportException when a location cannot be resolved or matches no file, or a file cannot be read
     */
    public List<ImportFile> read(final Collection<String> locations) {
        List<ImportFile> files = new ArrayList<>();
        for (String location : locations) {
            files.addAll(readLocation(location));
        }
        return files;
    }

    private List<ImportFile> readLocation(final String location) {
        logger.debug("Loading file location '{}'", location);
        Resource[] resources;
        try {
            resources = this.patternResolver.getResources(resourceLocation(location));
        } catch (IOException e) {
            throw new InvalidImportException("Unable to proceed location '" + location + "': " + e.getMessage(), e);
        }
        List<Resource> kept = Arrays.stream(resources).filter(this::isKept).toList();
        if (kept.isEmpty()) {
            throw new InvalidImportException("No files matching '" + location + "'!");
        }
        // One file per name: a resource found twice is read once
        Map<String, ImportFile> byName = new LinkedHashMap<>();
        kept.stream()
                .map(resource -> read(location, resource))
                .filter(file -> !file.content().isEmpty())
                .sorted(Comparator.comparing(ImportFile::name))
                .forEach(file -> byName.putIfAbsent(file.name(), file));
        return List.copyOf(byName.values());
    }

    private static String resourceLocation(final String location) {
        String resourceLocation = location.replaceFirst("^zip:", "jar:");
        // A location without a scheme is a local file
        return resourceLocation.contains(":") ? resourceLocation : "file:" + resourceLocation;
    }

    /**
     * Keeps the files to read: no directory, no hidden file unless {@code includeHiddenFiles}, and nothing matching
     * {@code excludes}. Resources that are not files (class path in a jar, URL) are kept.
     */
    private boolean isKept(final Resource resource) {
        if (!resource.isFile()) {
            return true;
        }
        File file;
        try {
            file = resource.getFile();
        } catch (IOException ignored) {
            return true;
        }
        if (file.isDirectory()) {
            return false;
        }
        if (!this.includeHiddenFiles && (FileUtils.isHidden(file) || FileUtils.hasHiddenAncestorDirectory(file))) {
            return false;
        }
        PathMatcher pathMatcher = this.patternResolver.getPathMatcher();
        // The patterns start with a slash and use slashes, as Unix paths: so must a Windows path (C:\x becomes /C:/x)
        String slashed = file.getPath().replace(File.separatorChar, '/');
        String path = slashed.startsWith("/") ? slashed : "/" + slashed;
        return this.excludes.stream()
                .map(pattern -> pattern.startsWith("**") ? "/" + pattern : pattern)
                .map(pattern -> !pattern.startsWith("/**") ? "/**" + pattern : pattern)
                .map(pattern -> !pattern.startsWith("/") ? "/" + pattern : pattern)
                .noneMatch(pattern -> {
                    boolean match = pathMatcher.match(pattern, path);
                    if (match) {
                        logger.debug("Excluding resource file '{}' (match {})", path, pattern);
                    }
                    return match;
                });
    }

    private static ImportFile read(final String location, final Resource found) {
        logger.debug("Loading file '{}'", found.getFilename());
        Resource resource = found;
        try {
            resource = withAuthentication(found);
            try (InputStream inputStream = resource.getInputStream()) {
                return new ImportFile(
                        location,
                        resource.getURI().toString(),
                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new InvalidImportException("Unable to proceed resource '" + resource + "': " + e.getMessage(), e);
        } finally {
            Authenticator.setDefault(null);
        }
    }

    /** Sets up basic authentication when the URL holds credentials, and masks them in the URL. */
    private static Resource withAuthentication(final Resource resource) throws IOException {
        String userInfo;
        try {
            userInfo = resource.getURL().getUserInfo();
        } catch (IOException e) {
            return resource;
        }
        if (userInfo == null) {
            return resource;
        }
        String[] credentials = userInfo.split(":");
        if (credentials.length != 2) {
            return resource;
        }
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(credentials[0], credentials[1].toCharArray());
            }
        });
        return new UrlResource(resource.getURI().toString().replace(userInfo + "@", "***@"));
    }
}
