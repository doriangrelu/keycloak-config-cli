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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.VersionUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
public class BaselineProvider {

    private static final Logger logger = LoggerFactory.getLogger(BaselineProvider.class);
    private static final String PLACEHOLDER = "REALM_NAME_PLACEHOLDER";

    private final ObjectMapper objectMapper;

    private final String fallbackVersion;

    private final List<String> versions = knownVersions();

    /** Versions whose reference was already replaced by another one: the warning is given once. */
    private final java.util.Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Autowired
    public BaselineProvider(ObjectMapper objectMapper, NormalizationOptions options) {
        this.objectMapper = objectMapper;
        this.fallbackVersion = options.fallbackVersion();
    }

    public RealmRepresentation getRealm(String version, String realmName) {
        try (var inputStream = getRealmInputStream(version)) {
            /*
             * Replace the placeholder with the realm name to import. This sets some internal values like role names,
             * baseUrls and redirectUrls so that they don't get picked up as "changes"
             */
            var realmString =
                    new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).replace(PLACEHOLDER, realmName);
            return objectMapper.readValue(realmString, RealmRepresentation.class);
        } catch (IOException ex) {
            throw new NormalizationException(
                    String.format("Failed to load baseline realm for version %s", version), ex);
        }
    }

    public ClientRepresentation getClient(String version, String clientId) {
        try (var is = getClientInputStream(version)) {
            var client = objectMapper.readValue(is, ClientRepresentation.class);
            client.setClientId(clientId);
            return client;
        } catch (IOException ex) {
            throw new NormalizationException(
                    String.format("Failed to load baseline client for version %s", version), ex);
        }
    }

    /**
     * The user profile Keycloak gives a new realm, for the reference of a version ({@code
     * baseline/<version>/user-profile/user-profile.json}); empty when that reference has none.
     */
    public java.util.Optional<UPConfig> getUserProfile(final String version) {
        String baseline = baselineVersion(version);
        try (var inputStream = getClass()
                .getResourceAsStream(String.format("/baseline/%s/user-profile/user-profile.json", baseline))) {
            return inputStream == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(objectMapper.readValue(inputStream, UPConfig.class));
        } catch (IOException ex) {
            throw new NormalizationException(
                    String.format("Failed to load the reference user profile for version %s", baseline), ex);
        }
    }

    /** The greatest version that has a reference realm. */
    public String latestVersion() {
        return this.versions.stream()
                .max(VersionUtil::compareVersions)
                .orElseThrow(() -> new NormalizationException("No reference realm is known"));
    }

    public InputStream getRealmInputStream(String version) {
        return open("realm/realm.json", version);
    }

    public InputStream getClientInputStream(String version) {
        return open("client/client.json", version);
    }

    /** The baseline of the version itself, else the fallback version when set, else the closest version. */
    String baselineVersion(String version) {
        if (this.versions.contains(version)) {
            return version;
        }
        if (this.fallbackVersion != null) {
            if (!this.versions.contains(this.fallbackVersion)) {
                throw new NormalizationException(String.format(
                        "Reference realm for version %s does not exist, "
                                + "and fallback version %s does not exist either. Aborting!",
                        version, this.fallbackVersion));
            }
            if (this.warned.add(version))
                logger.warn(
                        "Reference realm not found for version {}. Using fallback version {}!",
                        version,
                        this.fallbackVersion);
            return this.fallbackVersion;
        }
        var closest = closestVersion(version)
                .orElseThrow(() -> new NormalizationException(
                        String.format("Reference realm for version %s does not exist. Aborting!", version)));
        if (this.warned.add(version))
            logger.warn("Reference realm not found for version {}. Using the closest version {}!", version, closest);
        return closest;
    }

    /**
     * The closest known version: the greatest lower one of the same major version, else the lowest greater one of the
     * same major version, else the greatest lower one.
     */
    private static String majorOf(String version) {
        var separator = version.indexOf('.');
        return separator < 0 ? version : version.substring(0, separator);
    }

    private java.util.Optional<String> closestVersion(String version) {
        var major = majorOf(version);
        var sameMajor = this.versions.stream()
                .filter(known -> majorOf(known).equals(major))
                .toList();
        var lowerOfSameMajor = sameMajor.stream()
                .filter(known -> VersionUtil.le(known, version))
                .max(VersionUtil::compareVersions);
        if (lowerOfSameMajor.isPresent()) {
            return lowerOfSameMajor;
        }
        var greaterOfSameMajor = sameMajor.stream().min(VersionUtil::compareVersions);
        if (greaterOfSameMajor.isPresent()) {
            return greaterOfSameMajor;
        }
        return this.versions.stream()
                .filter(known -> VersionUtil.le(known, version))
                .max(VersionUtil::compareVersions);
    }

    private InputStream open(String file, String version) {
        var baseline = baselineVersion(version);
        var inputStream = getClass().getResourceAsStream(String.format("/baseline/%s/%s", baseline, file));
        if (inputStream == null) {
            throw new NormalizationException(
                    String.format("Reference %s for version %s does not exist. Aborting!", file, baseline));
        }
        return inputStream;
    }

    private static List<String> knownVersions() {
        try {
            var resources =
                    new PathMatchingResourcePatternResolver().getResources("classpath*:baseline/*/realm/realm.json");
            var versions = new ArrayList<String>();
            for (var resource : resources) {
                var path = resource.getURL().toString();
                var parts = path.split("/");
                versions.add(parts[parts.length - 3]);
            }
            return List.copyOf(versions);
        } catch (IOException ex) {
            throw new NormalizationException("Failed to list the reference realms", ex);
        }
    }
}
