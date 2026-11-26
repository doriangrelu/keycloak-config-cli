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

import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakAuthenticationException;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProvider;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Opens a connection per target, each in its own Spring context (ADR-010).
 *
 * <p>The context of a target is built on a copy of the environment of the application, with the {@code keycloak.*}
 * properties of the target first: it sees the defaults and the options given at start-up, and holds its own Keycloak
 * client. It is not a child of the application context, so its beans and its events stay apart; closing the
 * connection closes it, and with it the HTTP client.
 */
public final class ChildContextKeycloakConnector implements KeycloakConnector {

    /** Name of the property source that Spring Boot attaches to an environment, rebuilt for each copy. */
    private static final String ATTACHED_SOURCE = "configurationProperties";

    private final ConfigurableEnvironment mainEnvironment;
    private final Supplier<AnnotationConfigApplicationContext> contexts;

    /**
     * @param mainEnvironment the environment of the application, which is read and never changed
     */
    public ChildContextKeycloakConnector(final ConfigurableEnvironment mainEnvironment) {
        this(mainEnvironment, AnnotationConfigApplicationContext::new);
    }

    ChildContextKeycloakConnector(
            final ConfigurableEnvironment mainEnvironment,
            final Supplier<AnnotationConfigApplicationContext> contexts) {
        this.mainEnvironment = Objects.requireNonNull(mainEnvironment, "mainEnvironment");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Override
    public Connection connect(final Target target, final String secret) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(secret, "secret");
        AnnotationConfigApplicationContext context = this.contexts.get();
        try {
            context.setDisplayName("Keycloak target " + target.name());
            context.setEnvironment(environmentOf(target, secret));
            context.register(TargetConnectionConfiguration.class);
            context.refresh();
            String version = context.getBean(KeycloakProvider.class).getKeycloakVersion();
            return new TargetConnection(target, version, context);
        } catch (RuntimeException failure) {
            context.close();
            throw translated(target, failure);
        }
    }

    /** A copy of the environment of the application, in the same order, with the properties of the target first. */
    private ConfigurableEnvironment environmentOf(final Target target, final String secret) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.forEach(source -> sources.remove(source.getName()));
        for (PropertySource<?> source : this.mainEnvironment.getPropertySources()) {
            if (!ATTACHED_SOURCE.equals(source.getName())) {
                sources.addLast(source);
            }
        }
        sources.addFirst(new MapPropertySource(
                "target '" + target.name() + "'", TargetConnectionConfiguration.propertiesOf(target, secret)));
        environment.setActiveProfiles(this.mainEnvironment.getActiveProfiles());
        environment.setDefaultProfiles(this.mainEnvironment.getDefaultProfiles());
        environment.setConversionService(this.mainEnvironment.getConversionService());
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    /**
     * A failure of the first calls: the HTTP client could not reach Keycloak, or Keycloak answered the login with an
     * error. Any other failure is left as it is.
     */
    private static RuntimeException translated(final Target target, final RuntimeException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ProcessingException unreachable) {
                return new KeycloakUnreachableException(target, reasonOf(unreachable), failure);
            }
            if (current instanceof WebApplicationException answered && answered.getResponse() != null) {
                int status = answered.getResponse().getStatus();
                return switch (status) {
                    case 400, 401, 403, 404 -> new KeycloakAuthenticationException(target, status, failure);
                    case 429, 502, 503, 504 -> new KeycloakUnreachableException(target, "HTTP " + status, failure);
                    default -> failure;
                };
            }
        }
        return failure;
    }

    /** The deepest message of the HTTP client error: "Connection refused", "Connect timed out", the unknown host. */
    private static String reasonOf(final Throwable error) {
        String reason = error.getMessage();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                reason = current.getClass().getSimpleName() + ": " + current.getMessage();
            }
        }
        return reason;
    }
}
