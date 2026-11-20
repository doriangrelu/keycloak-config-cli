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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization;

import io.github.doriangrelu.keycloak.config.application.normalization.NormalizedRealm;
import io.github.doriangrelu.keycloak.config.application.normalization.RealmNormalization;
import io.github.doriangrelu.keycloak.config.application.normalization.port.ReferenceRealmGateway;
import io.github.doriangrelu.keycloak.config.properties.NormalizationKeycloakConfigProperties;
import java.util.Objects;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Normalizes an exported realm into an import file: the export is read into business objects, compared with the
 * reference realm of its Keycloak version ({@link RealmNormalization}), and what differs is written back.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "NORMALIZE")
public class RealmExportNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(RealmExportNormalizer.class);

    private final NormalizationKeycloakConfigProperties keycloakConfigProperties;
    private final RealmExportReader reader;
    private final RealmExportWriter writer;
    private final RealmNormalization normalization;

    RealmExportNormalizer(
            final NormalizationKeycloakConfigProperties keycloakConfigProperties,
            final RealmExportReader reader,
            final RealmExportWriter writer,
            final ReferenceRealmGateway referenceRealms) {
        this.keycloakConfigProperties = keycloakConfigProperties;
        this.reader = reader;
        this.writer = writer;
        this.normalization = new RealmNormalization(referenceRealms);
    }

    public RealmRepresentation normalize(final RealmRepresentation exported) {
        String exportVersion = exported.getKeycloakVersion();
        String keycloakVersion = this.keycloakConfigProperties.getVersion();
        if (!Objects.equals(exportVersion, keycloakVersion)) {
            logger.warn(
                    "Keycloak-Config-CLI keycloak version {} and export keycloak version {} are not equal."
                            + " This may cause problems if the API changed."
                            + " Please compile keycloak-config-cli with a matching keycloak version!",
                    keycloakVersion,
                    exportVersion);
        }
        String realm = exported.getRealm();
        logger.info("Normalizing realm {}", realm);

        NormalizedRealm normalized = this.normalization.normalize(realm, exportVersion, this.reader.read(exported));
        normalized
                .missing()
                .forEach(missing -> logger.warn(
                        "Default realm {} '{}' was deleted in exported realm '{}'. It may be reintroduced during"
                                + " import!",
                        missing.type().label().toLowerCase(java.util.Locale.ROOT),
                        missing.key(),
                        realm));

        RealmRepresentation result = this.writer.write(realm, normalized.resources());
        // The id of a realm is only kept when it is not its name
        if (!Objects.equals(exported.getId(), realm)) {
            result.setId(exported.getId());
        }
        return result;
    }
}
