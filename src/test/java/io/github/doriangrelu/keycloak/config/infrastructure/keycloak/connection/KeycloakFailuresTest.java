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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.application.definition.KeycloakAuthenticationException;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The failures of Keycloak during an import on a target are told like those of the connection (ADR-005). */
class KeycloakFailuresTest {

    private static final Target TARGET = new Target(
            "dev",
            "https://dev.example.org",
            "master",
            "admin-cli",
            GrantType.PASSWORD,
            Optional.of("admin"),
            true,
            Duration.ofSeconds(3));

    @Test
    void anUnreachableKeycloakDuringAnImportGivesCode3() {
        ProcessingException refused =
                new ProcessingException("connect failed", new ConnectException("Connection refused"));
        KeycloakAccessException failure =
                new KeycloakAccessException(KeycloakFailure.UNAVAILABLE, "Keycloak is unreachable", refused);

        RuntimeException translated = KeycloakFailures.duringImport(TARGET, failure);

        assertThat(translated)
                .isInstanceOf(KeycloakUnreachableException.class)
                .hasMessageContaining("'dev'")
                .hasMessageContaining("https://dev.example.org")
                .hasMessageContaining("Connection refused")
                .hasNoCause();
        assertThat(translated.getSuppressed()).containsExactly(failure);
        assertThat(ExitCodeResolver.resolve(translated).code()).isEqualTo(3);
    }

    @Test
    void aTimeoutDuringAnImportGivesCode3() {
        KeycloakAccessException failure = new KeycloakAccessException(KeycloakFailure.TIMEOUT, "read timeout");

        RuntimeException translated = KeycloakFailures.duringImport(TARGET, failure);

        assertThat(translated).isInstanceOf(KeycloakUnreachableException.class);
        assertThat(ExitCodeResolver.resolve(translated).code()).isEqualTo(3);
    }

    @Test
    void aRawProcessingErrorDuringAnImportGivesCode3() {
        RuntimeException translated =
                KeycloakFailures.duringImport(TARGET, new ProcessingException(new ConnectException("refused")));

        assertThat(translated).isInstanceOf(KeycloakUnreachableException.class).hasNoCause();
        assertThat(ExitCodeResolver.resolve(translated).code()).isEqualTo(3);
    }

    @Test
    void aRefusalDuringAnImportSaysWhatWasRefusedAndGivesCode4() {
        KeycloakAccessException failure = new KeycloakAccessException(
                KeycloakFailure.UNAUTHORIZED,
                403,
                "Keycloak responded with HTTP 403: {\"error\":\"unknown_error\"}",
                null);

        RuntimeException translated = KeycloakFailures.duringImport(TARGET, failure);

        assertThat(translated)
                .isInstanceOf(KeycloakAuthenticationException.class)
                .hasMessage("Keycloak at https://dev.example.org refused an operation of the import on target 'dev'"
                        + " (missing rights?): Keycloak responded with HTTP 403: {\"error\":\"unknown_error\"}")
                .hasNoCause();
        assertThat(translated.getMessage()).doesNotContain("login");
        assertThat(translated.getSuppressed()).containsExactly(failure);
        assertThat(ExitCodeResolver.resolve(translated).code()).isEqualTo(4);
    }

    @Test
    void aRefusalWithoutStatusNeverShowsHttp0() {
        KeycloakAccessException failure =
                new KeycloakAccessException(KeycloakFailure.UNAUTHORIZED, "Keycloak refused the operation");

        RuntimeException translated = KeycloakFailures.duringImport(TARGET, failure);

        assertThat(translated)
                .isInstanceOf(KeycloakAuthenticationException.class)
                .hasMessageEndingWith("(missing rights?): Keycloak refused the operation")
                .message()
                .doesNotContain("HTTP 0");
        assertThat(ExitCodeResolver.resolve(translated).code()).isEqualTo(4);
    }

    @Test
    void aRawRefusalDuringAnImportGivesCode4() {
        RuntimeException translated = KeycloakFailures.duringImport(TARGET, new WebApplicationException(401));

        assertThat(translated)
                .isInstanceOf(KeycloakAuthenticationException.class)
                .hasMessageContaining("refused an operation of the import on target 'dev'")
                .hasMessageEndingWith("HTTP 401");
        assertThat(ExitCodeResolver.resolve(translated).code()).isEqualTo(4);
    }

    @Test
    void aRejectedConfigurationIsLeftAsItIs() {
        KeycloakAccessException rejected = new KeycloakAccessException(
                KeycloakFailure.REJECTED, 400, "Keycloak responded with HTTP 400: invalid redirect", null);
        WebApplicationException notFound = new WebApplicationException(404);

        assertThat(KeycloakFailures.duringImport(TARGET, rejected)).isSameAs(rejected);
        assertThat(KeycloakFailures.duringImport(TARGET, notFound)).isSameAs(notFound);
    }

    @Test
    void anInvalidFileIsLeftAsItIs() {
        InvalidImportException invalid = new InvalidImportException("broken.json is not valid");

        assertThat(KeycloakFailures.duringImport(TARGET, invalid)).isSameAs(invalid);
        assertThat(ExitCodeResolver.resolve(invalid).code()).isEqualTo(2);
    }
}
