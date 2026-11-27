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
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakAuthenticationException;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakConfigProperties;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProvider;
import io.github.doriangrelu.keycloak.config.test.util.StubKeycloak;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The connection to a target, against a closed port, an unknown host or a stub of the Keycloak endpoints: no Keycloak
 * is needed (see the IT for a real one).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ChildContextKeycloakConnectorTest {

    private static final String SECRET = "a-secret-never-shown-4242";

    private static final String SERVER_VERSION = "26.5.3-stub";

    private ConfigurableEnvironment mainEnvironment;

    private static final String TOKEN = "/realms/master/protocol/openid-connect/token";
    private static final String SERVER_INFO = "/admin/serverinfo";

    private StubKeycloak keycloak;
    private int tokenStatus = 200;
    private String tokenBody = "{\"access_token\":\"stub-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}";
    private int serverInfoStatus = 200;

    /**
     * The main environment of the shell, with settings given at start-up that must never reach a target: each of them
     * would let a refused login or an unreachable server pass for a connection.
     */
    @BeforeEach
    void startTheShell() {
        GenericApplicationContext main = new GenericApplicationContext();
        TestPropertyValues.of(
                        "run.operation=SHELL",
                        "keycloak.password=main-password",
                        "keycloak.skip-server-info=true",
                        "keycloak.version=99.0.0",
                        "keycloak.http-proxy=http://localhost:2",
                        "keycloak.read-timeout=1ms",
                        "keycloak.availability-check.enabled=true")
                .applyTo(main);
        new ConfigDataApplicationContextInitializer().initialize(main);
        this.mainEnvironment = main.getEnvironment();
    }

    @BeforeEach
    void startAStubOfKeycloak() throws IOException {
        this.keycloak = new StubKeycloak();
        this.keycloak.route(TOKEN, () -> new StubKeycloak.Answer(this.tokenStatus, this.tokenBody));
        this.keycloak.route(
                SERVER_INFO,
                () -> new StubKeycloak.Answer(
                        this.serverInfoStatus, "{\"systemInfo\":{\"version\":\"" + SERVER_VERSION + "\"}}"));
    }

    @AfterEach
    void stopTheStub() throws IOException {
        this.keycloak.close();
    }

    @Test
    void connectsWithTheSecretAndReadsTheVersionFromTheServer() {
        try (Connection connection = connector().connect(target(stubUrl()), SECRET)) {
            assertThat(connection.keycloakVersion()).isEqualTo(SERVER_VERSION);
        }

        assertThat(this.keycloak.requestsTo(TOKEN))
                .singleElement()
                .satisfies(request -> assertThat(decoded(request.body()))
                        .contains("grant_type=password", "username=admin", "password=" + SECRET));
        assertThat(this.keycloak.requestsTo(SERVER_INFO))
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.authorization()).isEqualTo("Bearer stub-token"));
    }

    @Test
    void aRefusedPasswordIsNeverTakenForAConnection() {
        this.tokenStatus = 401;
        this.tokenBody = "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}";

        Throwable failure = catchThrowable(() -> connector().connect(target(stubUrl()), SECRET));

        assertThat(failure)
                .isInstanceOf(KeycloakAuthenticationException.class)
                .hasMessageContaining("'dev'")
                .hasMessageContaining(stubUrl())
                .hasMessageContaining("HTTP 401");
        assertThat(ExitCodeResolver.resolve(failure).code()).isEqualTo(4);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
        assertThat(this.keycloak.requestsTo(SERVER_INFO)).isEmpty();
    }

    @Test
    void aRefusedClientSecretIsNeverTakenForAConnection() {
        this.tokenStatus = 401;
        this.tokenBody = "{\"error\":\"unauthorized_client\",\"error_description\":\"Invalid client secret\"}";

        Throwable failure = catchThrowable(() -> connector().connect(clientTarget(stubUrl()), SECRET));

        assertThat(failure).isInstanceOf(KeycloakAuthenticationException.class);
        assertThat(ExitCodeResolver.resolve(failure).code()).isEqualTo(4);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
    }

    @Test
    void anInvalidGrantAnsweredWithABadRequestIsRefused() {
        this.tokenStatus = 400;
        this.tokenBody = "{\"error\":\"invalid_grant\",\"error_description\":\"Account disabled\"}";

        Throwable failure = catchThrowable(() -> connector().connect(target(stubUrl()), SECRET));

        assertThat(failure).isInstanceOf(KeycloakAuthenticationException.class);
        assertThat(ExitCodeResolver.resolve(failure).code()).isEqualTo(4);
    }

    @Test
    void aForbiddenServerInfoGivesAnUnknownVersionNotTheMainOne() {
        this.serverInfoStatus = 403;

        try (Connection connection = connector().connect(target(stubUrl()), SECRET)) {
            assertThat(connection.keycloakVersion()).isEqualTo("unknown");
        }
        assertThat(this.keycloak.requestsTo(TOKEN)).hasSize(1);
    }

    @Test
    void theSettingsOfTheMainEnvironmentNeverReachATarget() {
        try (Connection connection = connector().connect(target(stubUrl()), SECRET)) {
            KeycloakConfigProperties properties =
                    ((TargetConnection) connection).context().getBean(KeycloakConfigProperties.class);

            assertThat(properties.getPassword()).isEqualTo(SECRET);
            assertThat(properties.getClientSecret()).isEmpty();
            assertThat(properties.isSkipServerInfo()).isFalse();
            assertThat(properties.getVersion()).isEmpty();
            assertThat(properties.getHttpProxy()).isNull();
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getAvailabilityCheck().isEnabled()).isFalse();
        }
    }

    @Test
    void keycloakEnvironmentVariablesNeverReachATargetUnlikeTheOthers() {
        this.mainEnvironment
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource(
                        "test-systemEnvironment",
                        Map.of(
                                "KEYCLOAK_PASSWORD", "env-password",
                                "KEYCLOAK_SKIPSERVERINFO", "true",
                                "KEYCLOAK_HTTPPROXY", "http://localhost:2",
                                "IMPORT_DRYRUN", "true")));

        assertThat(this.mainEnvironment.getProperty("keycloak.skip-server-info"))
                .isEqualTo("true");
        assertThat(this.mainEnvironment.getProperty("import.dry-run")).isEqualTo("true");

        try (Connection connection = connector().connect(target(stubUrl()), SECRET)) {
            ConfigurableEnvironment target =
                    ((TargetConnection) connection).context().getEnvironment();

            assertThat(connection.keycloakVersion()).isEqualTo(SERVER_VERSION);
            assertThat(target.getProperty("keycloak.password")).isEqualTo(SECRET);
            assertThat(target.getProperty("keycloak.http-proxy")).isNull();
            assertThat(target.getProperty("import.dry-run")).isEqualTo("true");
        }
    }

    @Test
    void aRefusedLoginKeepsItsClearMessageAsTheDeepestCause() {
        this.tokenStatus = 401;

        Throwable failure = catchThrowable(() -> connector().connect(target(stubUrl()), SECRET));

        // an interactive shell prints the deepest cause: nothing may hide the message behind a client error
        assertThat(failure).isInstanceOf(KeycloakAuthenticationException.class).hasNoCause();
        assertThat(failure.getSuppressed()).hasSize(1);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
    }

    @Test
    void anUnreachableKeycloakKeepsItsClearMessageAsTheDeepestCause() throws IOException {
        Throwable failure =
                catchThrowable(() -> connector().connect(target("http://localhost:" + aClosedPort()), SECRET));

        assertThat(failure).isInstanceOf(KeycloakUnreachableException.class).hasNoCause();
        assertThat(failure.getSuppressed()).hasSize(1);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
    }

    @Test
    void theReadTimeoutIsNeverShorterThanTheConnectTimeout() {
        assertThat(TargetConnectionConfiguration.propertiesOf(targetWithConnectTimeout(Duration.ofSeconds(45)), SECRET))
                .containsEntry("keycloak.read-timeout", "PT45S");
        assertThat(TargetConnectionConfiguration.propertiesOf(targetWithConnectTimeout(Duration.ofSeconds(2)), SECRET))
                .containsEntry("keycloak.read-timeout", "10s");
    }

    private Target targetWithConnectTimeout(final Duration connectTimeout) {
        return new Target(
                "slow",
                stubUrl(),
                "master",
                "admin-cli",
                GrantType.PASSWORD,
                Optional.of("admin"),
                true,
                connectTimeout);
    }

    @Test
    void aClosedPortIsUnreachableAndFailsFast() throws IOException {
        String url = "http://localhost:" + aClosedPort();
        Target target = target(url);
        long start = System.nanoTime();

        Throwable failure = catchThrowable(() -> new ChildContextKeycloakConnector(
                        this.mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions())
                .connect(target, SECRET));

        assertThat(failure)
                .isInstanceOf(KeycloakUnreachableException.class)
                .hasMessageContaining("'dev'")
                .hasMessageContaining(url);
        assertThat(ExitCodeResolver.resolve(failure).code()).isEqualTo(3);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isLessThan(target.connectTimeout().plusSeconds(5));
    }

    @Test
    void anUnknownHostIsUnreachable() {
        Throwable failure = catchThrowable(() -> new ChildContextKeycloakConnector(
                        this.mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions())
                .connect(target("http://kcc-unknown-host.invalid:8080"), SECRET));

        assertThat(failure).isInstanceOf(KeycloakUnreachableException.class);
        assertThat(ExitCodeResolver.resolve(failure).code()).isEqualTo(3);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
    }

    @Test
    void aFailedConnectionClosesItsContextAndItsHttpClient() throws IOException {
        List<RecordingContext> contexts = new ArrayList<>();
        ChildContextKeycloakConnector connector = new ChildContextKeycloakConnector(
                this.mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions(), () -> {
                    RecordingContext context = new RecordingContext();
                    contexts.add(context);
                    return context;
                });

        catchThrowable(() -> connector.connect(target("http://localhost:" + aClosedPort()), SECRET));

        assertThat(contexts).singleElement().satisfies(context -> {
            assertThat(context.isActive()).isFalse();
            assertThat(context.properties).containsEntry("keycloak.password", SECRET);
            assertThat(context.provider).isNotNull();
            assertThat(((ResteasyClient) ReflectionTestUtils.getField(context.provider, "resteasyClient")).isClosed())
                    .isTrue();
        });
    }

    @Test
    void theContextOfATargetSeesTheMainPropertiesButNoKeycloakSetting() throws IOException {
        List<RecordingContext> contexts = new ArrayList<>();
        ChildContextKeycloakConnector connector = new ChildContextKeycloakConnector(
                this.mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions(), () -> {
                    RecordingContext context = new RecordingContext();
                    contexts.add(context);
                    return context;
                });
        int mainSources = this.mainEnvironment.getPropertySources().size();

        catchThrowable(() -> connector.connect(target("http://localhost:" + aClosedPort()), SECRET));

        assertThat(contexts.getFirst().properties)
                .containsEntry("run.operation", "IMPORT")
                .containsEntry("import.validate", "true")
                .containsEntry("keycloak.password", SECRET)
                .containsEntry("keycloak.http-proxy", null)
                .containsEntry("keycloak.read-timeout", "10s")
                .containsEntry("keycloak.availability-check.enabled", "false")
                .containsEntry("keycloak.client-secret", "")
                .containsEntry("keycloak.connect-timeout", "PT3S");
        assertThat(this.mainEnvironment.getProperty("run.operation")).isEqualTo("SHELL");
        assertThat(this.mainEnvironment.getProperty("keycloak.password")).isEqualTo("main-password");
        assertThat(this.mainEnvironment.getPropertySources().size()).isEqualTo(mainSources);
    }

    private ChildContextKeycloakConnector connector() {
        return new ChildContextKeycloakConnector(
                this.mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions());
    }

    private String stubUrl() {
        return this.keycloak.url();
    }

    private static Target clientTarget(final String url) {
        return new Target(
                "service",
                url,
                "master",
                "kcc-shell",
                GrantType.CLIENT_CREDENTIALS,
                Optional.empty(),
                true,
                Duration.ofSeconds(3));
    }

    private static String decoded(final String form) {
        return URLDecoder.decode(form, StandardCharsets.UTF_8);
    }

    private static Target target(final String url) {
        return new Target(
                "dev",
                url,
                "master",
                "admin-cli",
                GrantType.PASSWORD,
                Optional.of("admin"),
                true,
                Duration.ofSeconds(3));
    }

    private static int aClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String everythingPrintedBy(final Throwable failure) {
        StringWriter printed = new StringWriter();
        failure.printStackTrace(new PrintWriter(printed));
        return printed.toString();
    }

    /** Remembers what the context held before its beans are destroyed. */
    private static final class RecordingContext extends AnnotationConfigApplicationContext {
        private KeycloakProvider provider;
        private final Map<String, String> properties = new HashMap<>();

        @Override
        protected void destroyBeans() {
            for (String key : List.of(
                    "run.operation",
                    "import.validate",
                    "keycloak.http-proxy",
                    "keycloak.password",
                    "keycloak.client-secret",
                    "keycloak.read-timeout",
                    "keycloak.connect-timeout",
                    "keycloak.availability-check.enabled")) {
                this.properties.put(key, getEnvironment().getProperty(key));
            }
            for (String name : getBeanFactory().getBeanNamesForType(KeycloakProvider.class)) {
                if (getBeanFactory().containsSingleton(name)) {
                    this.provider = getBeanFactory().getBean(name, KeycloakProvider.class);
                }
            }
            super.destroyBeans();
        }
    }
}
