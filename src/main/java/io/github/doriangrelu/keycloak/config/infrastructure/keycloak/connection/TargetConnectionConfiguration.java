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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Import;

/**
 * The beans of the Spring context of one target: the Keycloak client of the command-line import, built from the
 * properties of the target. Its classes are registered by name, never found by scanning, so that the context holds
 * nothing else; the import will be added here (US-008-02). It carries no {@code @Configuration}, which component
 * scanning would pick up in the main context: {@code @Import} alone makes it a configuration class when registered.
 *
 * <p>The context runs with {@code run.operation=IMPORT}, the operation these beans are made for: the conditions they
 * carry for the main context hold in the context of a target without being changed.
 */
@Import({KeycloakClientFactory.class, KeycloakProvider.class})
public class TargetConnectionConfiguration {

    /** The read timeout of the command line, which a target does not set; it never waits less than it connects. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The properties of the context of a target. The secret is only given to the Keycloak client.
     *
     * <p>The context of a target gets no {@code keycloak.*} setting from the application, so the settings that matter
     * are given here: no skipped server info and no fallback version (the first call must reach the server), the
     * default read timeout, no availability check (one call, failing fast). No proxy: a target has none.
     */
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
        properties.put("keycloak.read-timeout", readTimeoutOf(target));
        properties.put("keycloak.skip-server-info", "false");
        properties.put("keycloak.version", "");
        properties.put("keycloak.availability-check.enabled", "false");
        return properties;
    }

    /** The default read timeout, or the connect timeout of the target when that one is larger. */
    private static String readTimeoutOf(final Target target) {
        return target.connectTimeout().compareTo(READ_TIMEOUT) > 0
                ? target.connectTimeout().toString()
                : "10s";
    }
}
