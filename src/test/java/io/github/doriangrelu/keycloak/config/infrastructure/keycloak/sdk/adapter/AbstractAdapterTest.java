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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakAccessException;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakFailure;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AbstractAdapterTest {

    @Test
    void returnsTheResultOfASuccessfulCall() {
        assertThat(TestAdapter.call(() -> "ok")).isEqualTo("ok");
    }

    @ParameterizedTest
    @CsvSource({
        "400, REJECTED",
        "422, REJECTED",
        "401, UNAUTHORIZED",
        "403, UNAUTHORIZED",
        "404, NOT_FOUND",
        "409, CONFLICT",
        "429, UNAVAILABLE",
        "503, UNAVAILABLE",
        "500, UNEXPECTED",
        "502, UNEXPECTED"
    })
    void translatesHttpErrors(int status, KeycloakFailure expected) {
        assertThatThrownBy(() -> TestAdapter.call(failingWith(status)))
                .isInstanceOfSatisfying(
                        KeycloakAccessException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(expected))
                .hasMessageContaining(String.valueOf(status));
    }

    @Test
    void keepsTheKeycloakErrorBodyInTheMessage() {
        Response response = Response.status(409)
                .entity("{\"errorMessage\":\"Role with name admin already exists\"}")
                .build();

        assertThatThrownBy(() -> TestAdapter.call(() -> {
                    throw new WebApplicationException(response);
                }))
                .hasMessageContaining("Role with name admin already exists");
    }

    @Test
    void keepsTheOriginalErrorAsCause() {
        WebApplicationException original =
                new WebApplicationException(Response.status(404).build());

        assertThatThrownBy(() -> TestAdapter.call(() -> {
                    throw original;
                }))
                .hasCause(original);
    }

    @Test
    void translatesReadTimeouts() {
        ProcessingException timeout = new ProcessingException(new SocketTimeoutException("Read timed out"));

        assertThatThrownBy(() -> TestAdapter.call(() -> {
                    throw timeout;
                }))
                .isInstanceOfSatisfying(
                        KeycloakAccessException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(KeycloakFailure.TIMEOUT));
    }

    @Test
    void translatesConnectionFailures() {
        ProcessingException refused = new ProcessingException(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> TestAdapter.call(() -> {
                    throw refused;
                }))
                .isInstanceOfSatisfying(
                        KeycloakAccessException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(KeycloakFailure.UNAVAILABLE));
    }

    @Test
    void findReturnsEmptyWhenKeycloakAnswersNotFound() {
        assertThat(TestAdapter.lookup(failingWith(404))).isEmpty();
    }

    @Test
    void findStillFailsOnOtherErrors() {
        assertThatThrownBy(() -> TestAdapter.lookup(failingWith(403)))
                .isInstanceOfSatisfying(
                        KeycloakAccessException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(KeycloakFailure.UNAUTHORIZED));
    }

    @Test
    void createReturnsTheIdFromTheLocationHeader() {
        Response created = Response.created(URI.create("https://keycloak/admin/realms/test/roles-by-id/1234-abcd"))
                .build();

        assertThat(TestAdapter.create(() -> created)).isEqualTo("1234-abcd");
    }

    @Test
    void createFailsWhenKeycloakDoesNotAnswerCreated() {
        Response conflict = Response.status(409).build();

        assertThatThrownBy(() -> TestAdapter.create(() -> conflict))
                .isInstanceOfSatisfying(
                        KeycloakAccessException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(KeycloakFailure.CONFLICT));
    }

    @Test
    void createFailsWhenTheLocationHeaderIsMissing() {
        Response createdWithoutLocation = Response.status(201).build();

        assertThatThrownBy(() -> TestAdapter.create(() -> createdWithoutLocation))
                .isInstanceOfSatisfying(
                        KeycloakAccessException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(KeycloakFailure.UNEXPECTED));
    }

    private static Supplier<String> failingWith(int status) {
        return () -> {
            throw new WebApplicationException(Response.status(status).build());
        };
    }

    /** Exposes the protected helpers of {@link AbstractAdapter} to the test. */
    private static final class TestAdapter extends AbstractAdapter {
        private TestAdapter() {
            super(() -> null);
        }

        static <T> T call(Supplier<T> call) {
            return execute(call);
        }

        static <T> Optional<T> lookup(Supplier<T> call) {
            return find(call);
        }

        static String create(Supplier<Response> creation) {
            return createAndGetId(creation);
        }
    }
}
