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

import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakClientFactory;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.KeycloakProvider;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The beans of the Spring context of one target: the Keycloak client of the command-line import, built from the
 * properties of the target. Its classes are registered by name, never found by scanning, so that the context holds
 * nothing else; the import will be added here (US-008-02).
 *
 * <p>The context runs with {@code run.operation=IMPORT}, the operation these beans are made for: the conditions they
 * carry for the main context hold in the context of a target without being changed.
 */
@Configuration(proxyBeanMethods = false)
@Import({KeycloakClientFactory.class, KeycloakProvider.class})
public class TargetConnectionConfiguration {

    /** The properties of the context of a target. The secret is only given to the Keycloak client. */
    static Map<String, Object> propertiesOf(final Target target, final String secret) {
        boolean password = target.grantType() == GrantType.PASSWORD;
        Map<String, Object> properties = new HashMap<>();
        properties.put("run.operation", "IMPORT");
        properties.put("keycloak.url", target.url());
        properties.put("keycloak.login-realm", target.loginRealm());
        properties.put("keycloak.client-id", target.clientId());
        properties.put("keycloak.grant-type", target.grantType().optionName());
        properties.put("keycloak.user", target.user().orElse(""));
        properties.put("keycloak.password", password ? secret : "");
        properties.put("keycloak.client-secret", password ? "" : secret);
        properties.put("keycloak.ssl-verify", Boolean.toString(target.sslVerify()));
        properties.put("keycloak.connect-timeout", target.connectTimeout().toString());
        properties.put("keycloak.availability-check.enabled", "false");
        return properties;
    }
}
