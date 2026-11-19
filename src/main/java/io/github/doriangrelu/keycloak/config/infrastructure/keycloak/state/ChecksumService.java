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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.state;

import io.github.doriangrelu.keycloak.config.application.importation.ChecksumChangedException;
import io.github.doriangrelu.keycloak.config.exception.InvalidImportException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RealmApi;
import io.github.doriangrelu.keycloak.config.model.RealmImport;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties.ImportBehaviorsProperties.ChecksumChangedOption;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class ChecksumService {
    private static final Logger logger = LoggerFactory.getLogger(ChecksumService.class);

    private final RealmApi realmApi;
    private final ImportConfigProperties importConfigProperties;

    @Autowired
    public ChecksumService(RealmApi realmApi, ImportConfigProperties importConfigProperties) {
        this.realmApi = realmApi;
        this.importConfigProperties = importConfigProperties;
    }

    public void doImport(RealmImport realmImport) {
        RealmRepresentation existingRealm = existingRealm(realmImport.getRealm());
        Map<String, String> customAttributes = existingRealm.getAttributes();

        String importChecksum = realmImport.getChecksum();
        String attributeKey = getCustomAttributeKey(realmImport);
        customAttributes.put(attributeKey, importChecksum);
        this.realmApi.updateRealm(existingRealm.getRealm(), existingRealm);

        logger.debug(
                "Updated import checksum of realm '{}' to '{}', attributeKey: '{}'",
                realmImport.getRealm(),
                importChecksum,
                attributeKey);
    }

    public boolean hasToBeUpdated(RealmImport realmImport) {
        RealmRepresentation existingRealm = existingRealm(realmImport.getRealm());
        if (existingRealm == null) {
            throw new InvalidImportException("The specified realm does not exist: " + realmImport.getRealm());
        }
        Map<String, String> customAttributes = existingRealm.getAttributes();

        String readChecksum = customAttributes.get(getCustomAttributeKey(realmImport));
        if (readChecksum == null) {
            return true;
        }

        if (Objects.equals(realmImport.getChecksum(), readChecksum)) {
            return false;
        }

        // checksum has changed
        if (ChecksumChangedOption.CONTINUE.equals(
                importConfigProperties.getBehaviors().getChecksumChanged())) {
            return true;
        } else if (ChecksumChangedOption.FAIL.equals(
                importConfigProperties.getBehaviors().getChecksumChanged())) {
            throw new ChecksumChangedException(realmImport.getSource(), readChecksum, realmImport.getChecksum());
        } else {
            throw new IllegalStateException("Unknown behavior constant: "
                    + importConfigProperties.getBehaviors().getChecksumChanged());
        }
    }

    @SuppressWarnings("java:S4790")
    private String getCustomAttributeKey(RealmImport realmImport) {
        String attributeSuffix;
        if (importConfigProperties.getBehaviors().isChecksumWithCacheKey()) {
            attributeSuffix = importConfigProperties.getCache().getKey();
        } else {
            attributeSuffix =
                    FilenameUtils.getName(realmImport.getSource()) + "_" + DigestUtils.md5Hex(realmImport.getSource());
        }

        return MessageFormat.format(ImportConfigProperties.REALM_CHECKSUM_ATTRIBUTE_PREFIX_KEY, attributeSuffix);
    }

    /** The realm, with the attributes and events flag Keycloak omits when they are empty. */
    private RealmRepresentation existingRealm(String realmName) {
        RealmRepresentation realm = realmApi.findRealm(realmName)
                .orElseThrow(() -> new InvalidImportException("The specified realm does not exist: " + realmName));
        if (realm.getAttributes() == null) {
            realm.setAttributes(new java.util.HashMap<>());
        }
        if (realm.isEventsEnabled() == null) {
            realm.setEventsEnabled(false);
        }
        return realm;
    }
}
