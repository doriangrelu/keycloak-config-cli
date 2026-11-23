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

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.domain.common.DomainException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.NormalizationException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakErrorTranslator;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProviderException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Optional;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

/**
 * Maps a failure to its {@link ExitCode} (ADR-005).
 *
 * <p>The cause chain is walked from the outermost error, and the first error with a known meaning decides. Legacy
 * exceptions and raw SDK errors are mapped too, until the code raising them is migrated.
 */
public final class ExitCodeResolver {

    private ExitCodeResolver() {}

    public static ExitCode resolve(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            Optional<ExitCode> exitCode = exitCodeOf(current);
            if (exitCode.isPresent()) {
                return exitCode.get();
            }
        }
        return ExitCode.UNEXPECTED_ERROR;
    }

    private static Optional<ExitCode> exitCodeOf(final Throwable error) {
        return switch (error) {
            case DomainException _ -> Optional.of(ExitCode.INVALID_CONFIGURATION);
            case ApplicationException _ -> Optional.of(ExitCode.INVALID_CONFIGURATION);
            case KeycloakAccessException keycloak -> exitCodeOf(keycloak);
            case WebApplicationException sdk -> exitCodeOf(KeycloakErrorTranslator.translate(sdk));
            case ProcessingException sdk -> exitCodeOf(KeycloakErrorTranslator.translate(sdk));
            case BindException _ -> Optional.of(ExitCode.INVALID_CONFIGURATION);
            case BindValidationException _ -> Optional.of(ExitCode.INVALID_CONFIGURATION);
            case NormalizationException _ -> Optional.of(ExitCode.INVALID_CONFIGURATION);
            case KeycloakProviderException _ -> Optional.of(ExitCode.KEYCLOAK_UNAVAILABLE);
            default -> Optional.empty();
        };
    }

    private static Optional<ExitCode> exitCodeOf(final KeycloakAccessException error) {
        return Optional.of(
                switch (error.getFailure()) {
                    case UNAVAILABLE, TIMEOUT -> ExitCode.KEYCLOAK_UNAVAILABLE;
                    case REJECTED, UNAUTHORIZED, NOT_FOUND, CONFLICT -> ExitCode.KEYCLOAK_REFUSED;
                    case UNEXPECTED -> ExitCode.UNEXPECTED_ERROR;
                });
    }
}
