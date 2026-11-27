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
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.KeycloakImportOptions;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Opens a connection per target, each in its own Spring context (ADR-010).
 *
 * <p>The context of a target is built on a copy of the environment of the application, in the same order, with the
 * properties of the target first: it sees the defaults and the options given at start-up, and holds its own Keycloak
 * client and the import (see {@link TargetConnectionConfiguration}), with the import options given here. The copy
 * leaves out every {@code keycloak.*} setting of the application, in any form ({@code --keycloak.password},
 * {@code KEYCLOAK_PASSWORD}...): the client of a target only knows what the target gives. The context is not a child of the application context, so its
 * beans and its events stay apart; closing the connection closes it, and with it the HTTP client.
 */
public final class ChildContextKeycloakConnector implements KeycloakConnector {

    private static final String KEYCLOAK_PREFIX = "keycloak";

    private final ConfigurableEnvironment mainEnvironment;
    private final TargetImportOptions importOptions;
    private final Supplier<AnnotationConfigApplicationContext> contexts;

    /**
     * @param mainEnvironment the environment of the application, which is read and never changed
     * @param importOptions the options of the imports on every target
     */
    public ChildContextKeycloakConnector(
            final ConfigurableEnvironment mainEnvironment, final TargetImportOptions importOptions) {
        this(mainEnvironment, importOptions, AnnotationConfigApplicationContext::new);
    }

    ChildContextKeycloakConnector(
            final ConfigurableEnvironment mainEnvironment,
            final TargetImportOptions importOptions,
            final Supplier<AnnotationConfigApplicationContext> contexts) {
        this.mainEnvironment = Objects.requireNonNull(mainEnvironment, "mainEnvironment");
        this.importOptions = Objects.requireNonNull(importOptions, "importOptions");
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
            context.registerBean("keycloakImportOptions", KeycloakImportOptions.class, this.importOptions::keycloak);
            context.register(TargetConnectionConfiguration.class);
            context.refresh();
            KeycloakProvider provider = context.getBean(KeycloakProvider.class);
            // the login first: reading the version alone takes a refused login for a server that hides its version
            provider.getInstance();
            String version = provider.getKeycloakVersion();
            return new TargetConnection(target, version, context, this.importOptions.syncUserFederation());
        } catch (RuntimeException failure) {
            context.close();
            throw KeycloakFailures.atConnection(target, failure);
        }
    }

    /** A copy of the environment of the application, in the same order, with the properties of the target first. */
    private ConfigurableEnvironment environmentOf(final Target target, final String secret) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.forEach(source -> sources.remove(source.getName()));
        this.mainEnvironment.getPropertySources().forEach(source -> sources.addLast(withoutKeycloakSettings(source)));
        sources.addFirst(new MapPropertySource(
                "target '" + target.name() + "'", TargetConnectionConfiguration.propertiesOf(target, secret)));
        environment.setActiveProfiles(this.mainEnvironment.getActiveProfiles());
        environment.setDefaultProfiles(this.mainEnvironment.getDefaultProfiles());
        environment.setConversionService(this.mainEnvironment.getConversionService());
        // replaces the attached source copied from the application, which wraps the sources of the application
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    /** A copy of the source without its {@code keycloak.*} settings; a source that cannot list its names is kept. */
    private static PropertySource<?> withoutKeycloakSettings(final PropertySource<?> source) {
        if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
            return source;
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        for (String name : enumerable.getPropertyNames()) {
            Object value = enumerable.getProperty(name);
            if (!isKeycloakSetting(name) && value != null) {
                kept.put(name, value);
            }
        }
        // an environment variable source keeps its type, which gives the relaxed names (IMPORT_FILES_LOCATIONS...)
        return source instanceof SystemEnvironmentPropertySource
                ? new SystemEnvironmentPropertySource(source.getName(), kept)
                : new MapPropertySource(source.getName(), kept);
    }

    /** {@code keycloak.url}, {@code keycloak.http-proxy}, {@code KEYCLOAK_PASSWORD}... */
    private static boolean isKeycloakSetting(final String name) {
        return name.split("[._\\[]", 2)[0].equalsIgnoreCase(KEYCLOAK_PREFIX);
    }
}
