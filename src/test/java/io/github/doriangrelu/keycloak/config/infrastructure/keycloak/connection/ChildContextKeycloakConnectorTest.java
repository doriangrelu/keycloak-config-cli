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

import io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProvider;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/** The connection to a target that cannot be reached: no Keycloak is needed (see the IT for the others). */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ChildContextKeycloakConnectorTest {

    private static final String SECRET = "a-secret-never-shown-4242";

    private ConfigurableEnvironment mainEnvironment;

    @BeforeEach
    void startTheShell() {
        GenericApplicationContext main = new GenericApplicationContext();
        TestPropertyValues.of("run.operation=SHELL", "keycloak.password=main-password")
                .applyTo(main);
        new ConfigDataApplicationContextInitializer().initialize(main);
        this.mainEnvironment = main.getEnvironment();
    }

    @Test
    void aClosedPortIsUnreachableAndFailsFast() throws IOException {
        String url = "http://localhost:" + aClosedPort();
        Target target = target(url);
        long start = System.nanoTime();

        Throwable failure =
                catchThrowable(() -> new ChildContextKeycloakConnector(this.mainEnvironment).connect(target, SECRET));

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
        Throwable failure = catchThrowable(() -> new ChildContextKeycloakConnector(this.mainEnvironment)
                .connect(target("http://kcc-unknown-host.invalid:8080"), SECRET));

        assertThat(failure).isInstanceOf(KeycloakUnreachableException.class);
        assertThat(ExitCodeResolver.resolve(failure).code()).isEqualTo(3);
        assertThat(everythingPrintedBy(failure)).doesNotContain(SECRET);
    }

    @Test
    void aFailedConnectionClosesItsContextAndItsHttpClient() throws IOException {
        List<RecordingContext> contexts = new ArrayList<>();
        ChildContextKeycloakConnector connector = new ChildContextKeycloakConnector(this.mainEnvironment, () -> {
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
    void theContextOfATargetSeesTheMainPropertiesUnderItsOwn() throws IOException {
        List<RecordingContext> contexts = new ArrayList<>();
        ChildContextKeycloakConnector connector = new ChildContextKeycloakConnector(this.mainEnvironment, () -> {
            RecordingContext context = new RecordingContext();
            contexts.add(context);
            return context;
        });
        int mainSources = this.mainEnvironment.getPropertySources().size();

        catchThrowable(() -> connector.connect(target("http://localhost:" + aClosedPort()), SECRET));

        assertThat(contexts.getFirst().properties)
                .containsEntry("run.operation", "IMPORT")
                .containsEntry("keycloak.read-timeout", "10s")
                .containsEntry("keycloak.availability-check.enabled", "false")
                .containsEntry("keycloak.client-secret", "")
                .containsEntry("keycloak.connect-timeout", "PT3S");
        assertThat(this.mainEnvironment.getProperty("run.operation")).isEqualTo("SHELL");
        assertThat(this.mainEnvironment.getProperty("keycloak.password")).isEqualTo("main-password");
        assertThat(this.mainEnvironment.getPropertySources().size()).isEqualTo(mainSources);
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
