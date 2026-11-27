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

package io.github.doriangrelu.keycloak.config.domain.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SecretVariableNamesTest {

    @Test
    void aClientSecretIsNamedAfterTheClientId() {
        assertThat(SecretVariableNames.client("shop")).isEqualTo("shopSecret");
        assertThat(SecretVariableNames.client("my-app")).isEqualTo("myAppSecret");
        assertThat(SecretVariableNames.client("{{env}}-api")).isEqualTo("apiSecret");
        assertThat(SecretVariableNames.client("1st")).isEqualTo("client1stSecret");
    }

    @Test
    void anIdentityProviderSecretIsNamedAfterTheAlias() {
        assertThat(SecretVariableNames.identityProvider("google")).isEqualTo("googleClientSecret");
    }

    @Test
    void theSmtpPasswordHasAFixedName() {
        assertThat(SecretVariableNames.smtpPassword()).isEqualTo("smtpPassword");
    }

    @Test
    void aBindCredentialIsNamedAfterTheComponent() {
        assertThat(SecretVariableNames.component("corporate ldap")).isEqualTo("corporateLdapBindCredential");
    }

    @Test
    void anotherHiddenSmtpEntryIsNamedAfterItsKey() {
        assertThat(SecretVariableNames.smtp("authTokenClientSecret")).isEqualTo("smtpAuthTokenClientSecret");
        assertThat(SecretVariableNames.smtp("password")).isEqualTo(SecretVariableNames.smtpPassword());
    }

    @Test
    void anotherHiddenIdentityProviderEntryIsNamedAfterTheAliasAndTheKey() {
        assertThat(SecretVariableNames.identityProvider("google", "clientAssertionKey"))
                .isEqualTo("googleClientAssertionKey");
        assertThat(SecretVariableNames.identityProvider("google", "clientSecret"))
                .isEqualTo(SecretVariableNames.identityProvider("google"));
        assertThat(SecretVariableNames.identityProvider("corp-saml", "signing.key"))
                .isEqualTo("corpSamlSigningKey");
    }

    @Test
    void aSecretOfAnAuthenticatorConfigIsNamedAfterItsAliasAndKey() {
        assertThat(SecretVariableNames.authenticatorConfig("captcha-action", "secret"))
                .isEqualTo("captchaActionSecret");
        assertThat(SecretVariableNames.authenticatorConfig("captcha", "api.key"))
                .isEqualTo("captchaApiKey");
    }

    @Test
    void aPrivateKeyOfAClientIsNamedAfterTheClientAndItsUse() {
        assertThat(SecretVariableNames.clientPrivateKey("shop", "signing")).isEqualTo("shopSigningPrivateKey");
        assertThat(SecretVariableNames.clientPrivateKey("https://saml.example.org", "encryption"))
                .isEqualTo("httpsSamlExampleOrgEncryptionPrivateKey");
    }

    @Test
    void aTakenNameGetsANumber() {
        assertThat(SecretVariableNames.unique("shopSecret", Set.of())).isEqualTo("shopSecret");
        assertThat(SecretVariableNames.unique("shopSecret", Set.of("shopSecret")))
                .isEqualTo("shopSecret2");
        assertThat(SecretVariableNames.unique("shopSecret", Set.of("shopSecret", "shopSecret2")))
                .isEqualTo("shopSecret3");
    }
}
