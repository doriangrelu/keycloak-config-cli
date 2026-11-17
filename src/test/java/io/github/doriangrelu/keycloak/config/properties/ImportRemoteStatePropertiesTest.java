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

package io.github.doriangrelu.keycloak.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties.ImportRemoteStateProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ImportRemoteStatePropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        // The CLI ships without an EL implementation, like Spring Boot does when none is present
        validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void plainStateNeedsNoSalt() {
        assertThat(validate(new ImportRemoteStateProperties(true, null, null))).isEmpty();
    }

    @Test
    void encryptedStateRequiresSalt() {
        Set<ConstraintViolation<ImportRemoteStateProperties>> violations =
                validate(new ImportRemoteStateProperties(true, "password", null));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("encryptionSaltProvidedWhenEncrypted");
    }

    @Test
    void encryptedStateRejectsBlankSalt() {
        assertThat(validate(new ImportRemoteStateProperties(true, "password", "  ")))
                .isNotEmpty();
    }

    @Test
    void encryptedStateAcceptsHexSalt() {
        assertThat(validate(new ImportRemoteStateProperties(true, "password", "0123456789ABCDEFabcdef")))
                .isEmpty();
    }

    @Test
    void saltMustBeHexEncoded() {
        assertThat(validate(new ImportRemoteStateProperties(true, "password", "not-hex")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("encryptionSalt");
    }

    @Test
    void noSaltIsShippedByDefault() {
        MapConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of("import.remote-state.encryption-key", "password"));

        ImportRemoteStateProperties properties = new Binder(source)
                .bind("import.remote-state", ImportRemoteStateProperties.class)
                .get();

        assertThat(properties.getEncryptionSalt()).isNull();
    }

    private static Set<ConstraintViolation<ImportRemoteStateProperties>> validate(
            ImportRemoteStateProperties properties) {
        return validator.validate(properties);
    }
}
