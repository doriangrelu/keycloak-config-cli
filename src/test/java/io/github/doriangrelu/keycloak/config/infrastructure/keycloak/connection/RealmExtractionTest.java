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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.BaselineProvider;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.NormalizationOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

class RealmExtractionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MASK = RealmExtraction.MASK;

    @Test
    void secretsAreRecognizedByTheirName() {
        assertThat(RealmExtraction.isSecretKey("secret")).isTrue();
        assertThat(RealmExtraction.isSecretKey("authTokenClientSecret")).isTrue();
        assertThat(RealmExtraction.isSecretKey("keystorePassword")).isTrue();
        assertThat(RealmExtraction.isSecretKey("jwt.private_key")).isTrue();
        assertThat(RealmExtraction.isSecretKey("api.key")).isTrue();
        assertThat(RealmExtraction.isSecretKey("API_KEY")).isTrue();
        assertThat(RealmExtraction.isSecretKey("site.key")).isFalse();
        assertThat(RealmExtraction.isSecretKey("clientId")).isFalse();
        // a setting about a password is no secret: only a name ending with the kind of secret is one
        assertThat(RealmExtraction.isSecretKey("require.password.update.after.registration"))
                .isFalse();
        assertThat(RealmExtraction.isSecretKey("secretExpirationTime")).isFalse();
    }

    @Test
    void theSecretsOfTheSmtpServerBecomeVariablesEvenInClear() {
        RealmRepresentation exported = new RealmRepresentation();
        exported.setSmtpServer(
                Map.of("host", "smtp.example.org", "authType", "token", "authTokenClientSecret", "clear-token-secret"));
        Set<String> taken = new HashSet<>();

        RealmExtraction.smtpSecrets(exported, taken);

        assertThat(exported.getSmtpServer())
                .containsEntry("authTokenClientSecret", "{{smtpAuthTokenClientSecret}}")
                .containsEntry("authType", "token");
        assertThat(taken).containsExactly("smtpAuthTokenClientSecret");
    }

    @Test
    void theSecretsOfAnIdentityProviderBecomeVariablesEvenInClear() {
        IdentityProviderRepresentation corporate = new IdentityProviderRepresentation();
        corporate.setAlias("corporate");
        corporate.setConfig(Map.of("clientId", "app", "jwtPrivateKey", "clear-private-key", "clientSecret", "clear"));
        Set<String> taken = new HashSet<>();

        RealmExtraction.identityProviderSecrets(List.of(corporate), taken);

        assertThat(corporate.getConfig())
                .containsEntry("clientId", "app")
                .containsEntry("clientSecret", "{{corporateClientSecret}}")
                .containsEntry("jwtPrivateKey", "{{corporateJwtPrivateKey}}");
    }

    @Test
    void theSecretsOfAnAuthenticatorConfigBecomeVariables() {
        AuthenticatorConfigRepresentation captcha = new AuthenticatorConfigRepresentation();
        captcha.setAlias("captcha");
        captcha.setConfig(
                Map.of("site.key", "public", "secret", "clear-secret", "api.key", MASK, "action", "register"));
        Set<String> taken = new HashSet<>();

        RealmExtraction.authenticatorConfigSecrets(List.of(captcha), taken);

        assertThat(captcha.getConfig())
                .containsEntry("site.key", "public")
                .containsEntry("action", "register")
                .containsEntry("secret", "{{captchaSecret}}")
                .containsEntry("api.key", "{{captchaApiKey}}");
        assertThat(taken).containsExactlyInAnyOrder("captchaSecret", "captchaApiKey");
    }

    @Test
    void theRotatedSecretOfAClientIsNeverWritten() {
        ClientRepresentation shop = new ClientRepresentation();
        shop.setClientId("shop");
        shop.setAttributes(Map.of("client.secret.rotated", "previous-secret", "kept", "yes"));
        List<String> warnings = new java.util.ArrayList<>();

        RealmExtraction.withoutClientSecretAttributes(List.of(shop), warnings);

        assertThat(shop.getAttributes()).containsOnlyKeys("kept");
        assertThat(warnings)
                .containsExactly("attributes.client.secret.rotated of client 'shop' is a secret and was not extracted");
    }

    @Test
    void everyHiddenEntryOfTheSmtpServerBecomesAVariable() {
        RealmRepresentation exported = new RealmRepresentation();
        exported.setSmtpServer(
                Map.of("host", "smtp.example.org", "password", MASK, "authTokenClientSecret", MASK, "user", "mailer"));
        Set<String> taken = new HashSet<>();

        RealmExtraction.smtpSecrets(exported, taken);

        assertThat(exported.getSmtpServer())
                .containsEntry("host", "smtp.example.org")
                .containsEntry("user", "mailer")
                .containsEntry("password", "{{smtpPassword}}")
                .containsEntry("authTokenClientSecret", "{{smtpAuthTokenClientSecret}}");
        assertThat(taken).containsExactlyInAnyOrder("smtpPassword", "smtpAuthTokenClientSecret");
    }

    @Test
    void everyHiddenEntryOfAnIdentityProviderConfigBecomesAVariable() {
        IdentityProviderRepresentation google = new IdentityProviderRepresentation();
        google.setAlias("google");
        google.setConfig(Map.of("clientId", "google-client", "clientSecret", MASK, "clientAssertionKey", MASK));
        Set<String> taken = new HashSet<>();

        RealmExtraction.identityProviderSecrets(List.of(google), taken);

        assertThat(google.getConfig())
                .containsEntry("clientId", "google-client")
                .containsEntry("clientSecret", "{{googleClientSecret}}")
                .containsEntry("clientAssertionKey", "{{googleClientAssertionKey}}");
        assertThat(taken).containsExactlyInAnyOrder("googleClientSecret", "googleClientAssertionKey");
    }

    @Test
    void thePrivateKeysOfASamlClientBecomeVariablesWhateverKeycloakGives() {
        ClientRepresentation saml = new ClientRepresentation();
        saml.setClientId("https://saml.example.org");
        saml.setAttributes(Map.of(
                "saml.signing.private.key", "MIIEclear",
                "saml.encryption.private.key", MASK,
                "saml.signing.certificate", "MIICcertificate"));
        ClientRepresentation owned = new ClientRepresentation();
        owned.setClientId("broker");
        owned.setAttributes(Map.of("saml.signing.private.key", "MIIEowned"));
        Set<String> taken = new HashSet<>();

        RealmExtraction.clientPrivateKeys(List.of(saml, owned), client -> "broker".equals(client.getClientId()), taken);

        assertThat(saml.getAttributes())
                .containsEntry("saml.signing.private.key", "{{httpsSamlExampleOrgSigningPrivateKey}}")
                .containsEntry("saml.encryption.private.key", "{{httpsSamlExampleOrgEncryptionPrivateKey}}")
                .containsEntry("saml.signing.certificate", "MIICcertificate");
        assertThat(owned.getAttributes()).doesNotContainKey("saml.signing.private.key");
        assertThat(taken)
                .containsExactlyInAnyOrder(
                        "httpsSamlExampleOrgSigningPrivateKey", "httpsSamlExampleOrgEncryptionPrivateKey");
    }

    @Test
    void theAttributesOfTheRealmAreWrittenWholeWithoutTheObsoleteOnes() {
        Map<String, String> exported = new java.util.LinkedHashMap<>();
        exported.put("frontendUrl", "https://sso.example.org");
        exported.put("cibaInterval", "5");
        exported.put("io.github.doriangrelu.keycloak.config.import-checksum-default", "abc");

        assertThat(RealmExtraction.attributesOf(exported, false))
                .isEqualTo(
                        JSON.createObjectNode().put("cibaInterval", "5").put("frontendUrl", "https://sso.example.org"));
        assertThat(RealmExtraction.attributesOf(exported, true)
                        .path("userProfileEnabled")
                        .asText())
                .isEqualTo("true");
        assertThat(RealmExtraction.attributesOf(Map.of(), false)).isNull();
        assertThat(RealmExtraction.attributesOf(null, true)
                        .path("userProfileEnabled")
                        .asText())
                .isEqualTo("true");
    }

    @Test
    void aMaskedValueLeftIsRemovedWithAWarningThatNamesItsElement() throws Exception {
        ObjectNode document = (ObjectNode) JSON.readTree("""
                {
                  "realm": "shop",
                  "smtpServer": { "host": "smtp.example.org", "authTokenClientSecret": "%1$s" },
                  "clients": [ { "clientId": "app", "attributes": { "jwt.secret": "%1$s", "kept": "yes" } } ],
                  "components": {
                    "org.keycloak.keys.KeyProvider": [
                      { "name": "imported-rsa", "config": { "privateKey": [ "%1$s" ], "priority": [ "100" ] } }
                    ]
                  }
                }
                """.formatted(MASK));

        List<String> warnings = RealmExtraction.withoutMaskedValues(document, "shop");

        assertThat(warnings)
                .containsExactly(
                        "smtpServer.authTokenClientSecret of realm 'shop' is hidden by Keycloak and was not extracted",
                        "attributes.jwt.secret of client 'app' is hidden by Keycloak and was not extracted",
                        "config.privateKey of component 'imported-rsa' is hidden by Keycloak and was not extracted");
        assertThat(document.toString()).doesNotContain(MASK).contains("\"kept\":\"yes\"", "\"priority\"");
    }

    @Test
    void noWarningWithoutMaskedValue() throws Exception {
        ObjectNode document = (ObjectNode) JSON.readTree("{ \"realm\": \"shop\", \"enabled\": true }");

        assertThat(RealmExtraction.withoutMaskedValues(document, "shop")).isEmpty();
    }

    @Test
    void withoutVersionTheLatestReferenceIsUsed() {
        BaselineProvider baselines = new BaselineProvider(new ObjectMapper(), new NormalizationOptions(null, null));

        assertThat(RealmExtraction.referenceVersion(null, baselines)).isEqualTo(baselines.latestVersion());
        assertThat(RealmExtraction.referenceVersion(" ", baselines)).isEqualTo(baselines.latestVersion());
        assertThat(RealmExtraction.referenceVersion("26.4.0", baselines)).isEqualTo("26.4.0");
    }
}
