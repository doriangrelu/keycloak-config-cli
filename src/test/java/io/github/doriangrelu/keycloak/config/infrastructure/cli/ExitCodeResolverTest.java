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

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.application.importation.ChecksumChangedException;
import io.github.doriangrelu.keycloak.config.domain.common.DomainException;
import io.github.doriangrelu.keycloak.config.exception.ImportProcessingException;
import io.github.doriangrelu.keycloak.config.exception.InvalidImportException;
import io.github.doriangrelu.keycloak.config.exception.KeycloakProviderException;
import io.github.doriangrelu.keycloak.config.exception.KeycloakRepositoryException;
import io.github.doriangrelu.keycloak.config.exception.NormalizationException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import io.github.doriangrelu.keycloak.config.properties.ImportConfigProperties;
import jakarta.validation.Validation;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.ConnectException;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

class ExitCodeResolverTest {

    @Test
    void exitCodesArePublicValues() {
        assertThat(ExitCode.SUCCESS.code()).isZero();
        assertThat(ExitCode.UNEXPECTED_ERROR.code()).isEqualTo(1);
        assertThat(ExitCode.INVALID_CONFIGURATION.code()).isEqualTo(2);
        assertThat(ExitCode.KEYCLOAK_UNAVAILABLE.code()).isEqualTo(3);
        assertThat(ExitCode.KEYCLOAK_REFUSED.code()).isEqualTo(4);
        assertThat(ExitCode.CHECKSUM_CHANGED.code()).isEqualTo(5);
        assertThat(ExitCode.PENDING_CHANGES.code()).isEqualTo(10);
    }

    @Test
    void unknownErrorsAreUnexpected() {
        assertThat(ExitCodeResolver.resolve(new IllegalStateException("boom"))).isEqualTo(ExitCode.UNEXPECTED_ERROR);
    }

    @Test
    void domainErrorsAreInvalidConfiguration() {
        assertThat(ExitCodeResolver.resolve(new DomainException("duplicated key") {}))
                .isEqualTo(ExitCode.INVALID_CONFIGURATION);
    }

    @Test
    void applicationErrorsAreInvalidConfiguration() {
        assertThat(ExitCodeResolver.resolve(new ApplicationException("cycle") {}))
                .isEqualTo(ExitCode.INVALID_CONFIGURATION);
    }

    @Test
    void changedChecksumHasItsOwnCode() {
        assertThat(ExitCodeResolver.resolve(new ChecksumChangedException("realm.json", "a", "b")))
                .isEqualTo(ExitCode.CHECKSUM_CHANGED);
    }

    @ParameterizedTest
    @CsvSource({
        "REJECTED, KEYCLOAK_REFUSED",
        "UNAUTHORIZED, KEYCLOAK_REFUSED",
        "NOT_FOUND, KEYCLOAK_REFUSED",
        "CONFLICT, KEYCLOAK_REFUSED",
        "UNAVAILABLE, KEYCLOAK_UNAVAILABLE",
        "TIMEOUT, KEYCLOAK_UNAVAILABLE",
        "UNEXPECTED, UNEXPECTED_ERROR"
    })
    void keycloakFailuresAreMappedByCause(KeycloakFailure failure, ExitCode expected) {
        assertThat(ExitCodeResolver.resolve(new KeycloakAccessException(failure, "failed")))
                .isEqualTo(expected);
    }

    @Test
    void invalidPropertiesAtStartupAreInvalidConfiguration() {
        SpringValidatorAdapter validator = new SpringValidatorAdapter(Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator());
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("import.dry-run-format", "xml")));

        Throwable startupFailure = catchThrowable(() ->
                binder.bind("import", Bindable.of(ImportConfigProperties.class), new ValidationBindHandler(validator)));

        assertThat(startupFailure).isNotNull();
        assertThat(ExitCodeResolver.resolve(new IllegalStateException("context failed", startupFailure)))
                .isEqualTo(ExitCode.INVALID_CONFIGURATION);
    }

    @Test
    void legacyImportErrorsAreInvalidConfiguration() {
        assertThat(ExitCodeResolver.resolve(new InvalidImportException("Unable to parse file")))
                .isEqualTo(ExitCode.INVALID_CONFIGURATION);
        assertThat(ExitCodeResolver.resolve(
                        new KeycloakRepositoryException("Cannot find client by clientId '%s'", "x")))
                .isEqualTo(ExitCode.INVALID_CONFIGURATION);
        assertThat(ExitCodeResolver.resolve(new NormalizationException("unsupported")))
                .isEqualTo(ExitCode.INVALID_CONFIGURATION);
    }

    @Test
    void keycloakUnreachableAtStartupIsUnavailable() {
        assertThat(ExitCodeResolver.resolve(new KeycloakProviderException("Could not connect to keycloak")))
                .isEqualTo(ExitCode.KEYCLOAK_UNAVAILABLE);
    }

    @Test
    void rawSdkErrorsOfLegacyCodeAreTranslated() {
        assertThat(ExitCodeResolver.resolve(
                        new WebApplicationException(Response.status(403).build())))
                .isEqualTo(ExitCode.KEYCLOAK_REFUSED);
        assertThat(ExitCodeResolver.resolve(new ProcessingException(new ConnectException("refused"))))
                .isEqualTo(ExitCode.KEYCLOAK_UNAVAILABLE);
    }

    @Test
    void wrappedErrorsAreResolvedFromTheirCause() {
        ImportProcessingException wrapped = new ImportProcessingException(
                "Cannot create client",
                new WebApplicationException(Response.status(400).build()));

        assertThat(ExitCodeResolver.resolve(wrapped)).isEqualTo(ExitCode.KEYCLOAK_REFUSED);
    }

    @Test
    void theOutermostKnownErrorWins() {
        ChecksumChangedException checksum = new ChecksumChangedException("realm.json", "a", "b");
        RuntimeException wrapper =
                new RuntimeException(new KeycloakAccessException(KeycloakFailure.TIMEOUT, "t", checksum));

        assertThat(ExitCodeResolver.resolve(wrapper)).isEqualTo(ExitCode.KEYCLOAK_UNAVAILABLE);
    }
}
