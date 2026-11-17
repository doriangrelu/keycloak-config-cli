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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakErrorTranslator;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;

/**
 * Base class of the SDK adapters (ADR-003). Errors of the Keycloak Admin client are always translated into
 * {@link KeycloakAccessException} by {@link KeycloakErrorTranslator}.
 *
 * <p>Every SDK call of an adapter goes through {@link #execute(Supplier)}, {@link #find(Supplier)} or
 * {@link #createAndGetId(Supplier)}. Adapters never catch {@link WebApplicationException} or
 * {@link ProcessingException} themselves.
 */
public abstract class AbstractAdapter {

    private static final int CREATED = 201;

    private final Supplier<Keycloak> keycloak;

    /**
     * @param keycloak supplies the current client; it is looked up on every call so that the provider can renew it
     *     when the access token expires
     */
    protected AbstractAdapter(final Supplier<Keycloak> keycloak) {
        this.keycloak = Objects.requireNonNull(keycloak, "keycloak");
    }

    protected Keycloak keycloak() {
        return this.keycloak.get();
    }

    protected static <T> T execute(final Supplier<T> call) {
        try {
            return call.get();
        } catch (final WebApplicationException error) {
            throw KeycloakErrorTranslator.translate(error);
        } catch (final ProcessingException error) {
            throw KeycloakErrorTranslator.translate(error);
        }
    }

    protected static void execute(final Runnable call) {
        execute(() -> {
            call.run();
            return null;
        });
    }

    /** Reads a resource that may not exist: a 404 answer gives an empty result instead of an error. */
    protected static <T> Optional<T> find(final Supplier<T> call) {
        try {
            return Optional.ofNullable(execute(call));
        } catch (final KeycloakAccessException error) {
            if (error.getFailure() == KeycloakFailure.NOT_FOUND) {
                return Optional.empty();
            }
            throw error;
        }
    }

    /** Runs a creation call, requires a {@code 201 Created} answer and returns the id of the created resource. */
    protected static String createAndGetId(final Supplier<Response> creation) {
        try (Response response = execute(creation)) {
            if (response.getStatus() != CREATED) {
                throw KeycloakErrorTranslator.translate(response, null);
            }
            String location = response.getHeaderString("Location");
            if (location == null || location.isBlank()) {
                throw new KeycloakAccessException(
                        KeycloakFailure.UNEXPECTED, "Keycloak created the resource but returned no Location header");
            }
            return location.substring(location.lastIndexOf('/') + 1);
        }
    }
}
