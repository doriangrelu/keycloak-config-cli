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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.MessageBundleAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.RequiredActionAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.RoleAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.MessageBundleApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RequiredActionApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import io.github.doriangrelu.keycloak.config.provider.KeycloakProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the SDK adapters (ADR-003). They are not Spring components themselves: they only receive the current
 * Keycloak client, supplied by {@link KeycloakProvider}, which renews it when needed.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class KeycloakClientFactory {

    @Bean
    public MessageBundleApi messageBundleApi(final KeycloakProvider keycloakProvider) {
        return new MessageBundleAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public RequiredActionApi requiredActionApi(final KeycloakProvider keycloakProvider) {
        return new RequiredActionAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public RoleApi roleApi(final KeycloakProvider keycloakProvider) {
        return new RoleAdapter(keycloakProvider::getInstance);
    }
}
