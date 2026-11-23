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

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.AuthenticationFlowAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.ClientAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.ClientAuthorizationAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.ClientPolicyAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.ClientScopeAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.ComponentAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.GroupAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.IdentityProviderAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.MessageBundleAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.RealmAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.RequiredActionAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.RoleAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.ScopeMappingAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.UserAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter.UserProfileAdapter;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.AuthenticationFlowApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientAuthorizationApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientPolicyApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ClientScopeApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ComponentApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.GroupApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.IdentityProviderApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.MessageBundleApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RealmApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RequiredActionApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RoleApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.ScopeMappingApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserApi;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.UserProfileApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the SDK adapters (ADR-003). They are not Spring components themselves: they only receive the current
 * Keycloak client, supplied by {@link KeycloakProvider}, which renews it when needed.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
@EnableConfigurationProperties(KeycloakConfigProperties.class)
public class KeycloakClientFactory {

    @Bean
    public ClientApi clientApi(final KeycloakProvider keycloakProvider) {
        return new ClientAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public ClientScopeApi clientScopeApi(final KeycloakProvider keycloakProvider) {
        return new ClientScopeAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public GroupApi groupApi(final KeycloakProvider keycloakProvider) {
        return new GroupAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public UserApi userApi(final KeycloakProvider keycloakProvider) {
        return new UserAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public IdentityProviderApi identityProviderApi(final KeycloakProvider keycloakProvider) {
        return new IdentityProviderAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public ClientPolicyApi clientPolicyApi(final KeycloakProvider keycloakProvider) {
        return new ClientPolicyAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public AuthenticationFlowApi authenticationFlowApi(final KeycloakProvider keycloakProvider) {
        return new AuthenticationFlowAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public ClientAuthorizationApi clientAuthorizationApi(final KeycloakProvider keycloakProvider) {
        return new ClientAuthorizationAdapter(
                keycloakProvider::getInstance,
                () -> keycloakProvider.getCustomApiProxy(ManagementPermissions.class),
                keycloakProvider::isFgapV2Active);
    }

    @Bean
    public RealmApi realmApi(final KeycloakProvider keycloakProvider) {
        return new RealmAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public ComponentApi componentApi(final KeycloakProvider keycloakProvider) {
        return new ComponentAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public UserProfileApi userProfileApi(final KeycloakProvider keycloakProvider) {
        return new UserProfileAdapter(keycloakProvider::getInstance);
    }

    @Bean
    public ScopeMappingApi scopeMappingApi(final KeycloakProvider keycloakProvider) {
        return new ScopeMappingAdapter(keycloakProvider::getInstance);
    }

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
