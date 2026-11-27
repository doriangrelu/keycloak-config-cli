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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.ExtractedRealm;
import io.github.doriangrelu.keycloak.config.application.definition.RealmNotFoundOnTargetException;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.DryRunReport;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCodeResolver;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.BaselineProvider;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.NormalizationOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.containers.GenericContainer;

/** The extraction of a hand-made realm from a connected target, against the Keycloak of the integration tests. */
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class TargetExtractIT {

    private static final GenericContainer<?> KEYCLOAK = AbstractImportIT.KEYCLOAK_CONTAINER;
    private static final String URL = System.getProperty("keycloak.url");
    private static final String ADMIN = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN");
    private static final String ADMIN_PASSWORD = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN_PASSWORD");

    private static final String REALM = "target-extract";
    private static final String DEFAULT_REALM = "target-extract-default";
    private static final String PROFILE_REALM = "target-extract-profile";
    private static final String AUTHZ_REALM = "target-extract-authz";
    private static final String API_SECRET = "api-secret-value-5e1c8b";
    private static final String SHOP_SECRET = "shop-secret-value-4f8a2c";
    private static final String GOOGLE_SECRET = "google-secret-value-9d1e7b";
    private static final String SMTP_PASSWORD = "smtp-password-value-3c6f0a";
    private static final String USER_PASSWORD = "alice-password-value-7b2e9d";
    private static final String MASK = "**********";
    private static final String SIGNING_KEY = privateKey();
    private static final String ENCRYPTION_KEY = privateKey();
    private static final String CAPTCHA_SECRET = "captcha-secret-value-8a3f1d";
    private static final String CAPTCHA_API_KEY = "captcha-api-key-value-2b7e4c";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ConfigurableEnvironment mainEnvironment;

    @BeforeAll
    static void createTheRealm() {
        GenericApplicationContext main = new GenericApplicationContext();
        TestPropertyValues.of("run.operation=SHELL").applyTo(main);
        new ConfigDataApplicationContextInitializer().initialize(main);
        mainEnvironment = main.getEnvironment();
        removeTheRealm();
        try (Keycloak admin = admin()) {
            RealmRepresentation realm = new RealmRepresentation();
            realm.setRealm(REALM);
            realm.setEnabled(true);
            realm.setLoginWithEmailAllowed(false);
            // a setting Keycloak keeps as an attribute of the realm
            realm.setAttributes(Map.of("frontendUrl", "https://sso.example.org"));
            realm.setSmtpServer(Map.of(
                    "host", "smtp.example.org",
                    "port", "587",
                    "from", "noreply@example.org",
                    "auth", "true",
                    "user", "mailer",
                    "password", SMTP_PASSWORD));
            admin.realms().create(realm);
            RealmResource created = admin.realm(REALM);

            ClientRepresentation shop = new ClientRepresentation();
            shop.setClientId("shop");
            shop.setPublicClient(false);
            shop.setSecret(SHOP_SECRET);
            shop.setServiceAccountsEnabled(true);
            shop.setRedirectUris(List.of("https://shop.example.org/*"));
            created.clients().create(shop).close();
            ClientRepresentation web = new ClientRepresentation();
            web.setClientId("web");
            web.setPublicClient(true);
            web.setRedirectUris(List.of("https://web.example.org/*"));
            created.clients().create(web).close();
            ClientRepresentation saml = new ClientRepresentation();
            saml.setClientId("https://saml.example.org");
            saml.setProtocol("saml");
            saml.setRedirectUris(List.of("https://saml.example.org/*"));
            saml.setAttributes(Map.of(
                    "saml.client.signature",
                    "true",
                    "saml.encrypt",
                    "true",
                    "saml.signing.private.key",
                    SIGNING_KEY,
                    "saml.encryption.private.key",
                    ENCRYPTION_KEY));
            created.clients().create(saml).close();

            created.roles().create(role("manager"));
            String shopId = created.clients().findByClientId("shop").getFirst().getId();
            created.clients().get(shopId).roles().create(role("viewer"));

            GroupRepresentation staff = new GroupRepresentation();
            staff.setName("staff");
            created.groups().add(staff).close();
            String staffId = created.groups().groups().stream()
                    .filter(group -> "staff".equals(group.getName()))
                    .findFirst()
                    .orElseThrow()
                    .getId();
            GroupRepresentation admins = new GroupRepresentation();
            admins.setName("admins");
            created.groups().group(staffId).subGroup(admins).close();
            created.groups()
                    .group(staffId)
                    .roles()
                    .realmLevel()
                    .add(List.of(created.roles().get("manager").toRepresentation()));
            String adminsId = created.getGroupByPath("/staff/admins").getId();
            created.groups()
                    .group(adminsId)
                    .roles()
                    .clientLevel(shopId)
                    .add(List.of(
                            created.clients().get(shopId).roles().get("viewer").toRepresentation()));

            IdentityProviderRepresentation google = new IdentityProviderRepresentation();
            google.setAlias("google");
            google.setProviderId("google");
            google.setEnabled(true);
            // as the admin console creates it
            google.setFirstBrokerLoginFlowAlias("first broker login");
            google.setConfig(Map.of("clientId", "google-client", "clientSecret", GOOGLE_SECRET));
            created.identityProviders().create(google).close();

            UserRepresentation alice = new UserRepresentation();
            alice.setUsername("alice");
            alice.setEnabled(true);
            alice.setEmail("alice@example.org");
            alice.setFirstName("Alice");
            alice.setLastName("Martin");
            CredentialRepresentation password = new CredentialRepresentation();
            password.setType(CredentialRepresentation.PASSWORD);
            password.setValue(USER_PASSWORD);
            password.setTemporary(false);
            alice.setCredentials(List.of(password));
            created.users().create(alice).close();
            String aliceId = created.users().search("alice", true).getFirst().getId();
            created.users()
                    .get(aliceId)
                    .roles()
                    .realmLevel()
                    .add(List.of(created.roles().get("manager").toRepresentation()));
            created.users().get(aliceId).joinGroup(adminsId);

            created.roles().deleteRole("offline_access");

            RealmRepresentation byDefault = new RealmRepresentation();
            byDefault.setRealm(DEFAULT_REALM);
            byDefault.setEnabled(true);
            admin.realms().create(byDefault);

            RealmRepresentation withProfile = new RealmRepresentation();
            withProfile.setRealm(PROFILE_REALM);
            withProfile.setEnabled(true);
            admin.realms().create(withProfile);
            UPConfig profile = admin.realm(PROFILE_REALM).users().userProfile().getConfiguration();
            UPAttribute department = new UPAttribute("department");
            department.setDisplayName("Department");
            department.setPermissions(new UPAttributePermissions(Set.of("admin", "user"), Set.of("admin")));
            profile.addOrReplaceAttribute(department);
            admin.realm(PROFILE_REALM).users().userProfile().update(profile);

            RealmRepresentation withAuthorization = new RealmRepresentation();
            withAuthorization.setRealm(AUTHZ_REALM);
            withAuthorization.setEnabled(true);
            admin.realms().create(withAuthorization);
            ClientRepresentation api = new ClientRepresentation();
            api.setClientId("api");
            api.setPublicClient(false);
            api.setSecret(API_SECRET);
            api.setServiceAccountsEnabled(true);
            api.setAuthorizationServicesEnabled(true);
            admin.realm(AUTHZ_REALM).clients().create(api).close();

            // an authenticator config with a secret, as the reCAPTCHA of the registration form holds
            created.flows().copy("registration", Map.of("newName", "registration with captcha"));
            String form = "registration with captcha registration form";
            for (String provider : List.of("registration-recaptcha-action", "registration-recaptcha-enterprise")) {
                try {
                    created.flows().addExecution(form, Map.of("provider", provider));
                } catch (RuntimeException unknownProvider) {
                    continue;
                }
                String executionId = created.flows().getExecutions(form).stream()
                        .filter(execution -> provider.equals(execution.getProviderId()))
                        .findFirst()
                        .orElseThrow()
                        .getId();
                org.keycloak.representations.idm.AuthenticatorConfigRepresentation config =
                        new org.keycloak.representations.idm.AuthenticatorConfigRepresentation();
                config.setAlias(provider.replace("registration-recaptcha", "captcha"));
                config.setConfig(Map.of(
                        "site.key",
                        "planted-site-key",
                        "secret",
                        CAPTCHA_SECRET,
                        "api.key",
                        CAPTCHA_API_KEY,
                        "project.id",
                        "planted-project"));
                created.flows().newExecutionConfig(executionId, config).close();
            }
            RealmRepresentation bindings = created.toRepresentation();
            bindings.setRegistrationFlow("registration with captcha");
            created.update(bindings);
        }
    }

    /** The realm lives in the container shared by the other ITs: it is removed. */
    @AfterAll
    static void removeTheRealm() {
        try (Keycloak admin = admin()) {
            admin.realms().findAll().stream()
                    .filter(realm -> List.of(REALM, DEFAULT_REALM, PROFILE_REALM, AUTHZ_REALM)
                            .contains(realm.getRealm()))
                    .forEach(realm -> admin.realm(realm.getRealm()).remove());
        }
    }

    @Test
    void theDocumentHoldsTheConfiguredElements() throws Exception {
        JsonNode document = document(extract(false));

        assertThat(document.path("realm").asText()).isEqualTo(REALM);
        assertThat(document.path("loginWithEmailAllowed").asBoolean(true)).isFalse();
        assertThat(clientIds(document)).contains("shop", "web");
        assertThat(names(document.path("roles").path("realm"))).contains("manager");
        assertThat(names(document.path("roles").path("client").path("shop"))).containsExactly("viewer");
        JsonNode staff = byName(document.path("groups"), "staff");
        assertThat(names(staff.path("subGroups"))).containsExactly("admins");
        assertThat(staff.path("realmRoles").get(0).asText()).isEqualTo("manager");
        assertThat(byField(document.path("identityProviders"), "alias", "google"))
                .isNotNull();
        assertThat(document.path("smtpServer").path("host").asText()).isEqualTo("smtp.example.org");
        assertThat(document.has("id")).isFalse();
        // a setting kept as an attribute, with the other attributes: the import replaces them whole
        assertThat(document.path("attributes").path("frontendUrl").asText()).isEqualTo("https://sso.example.org");
        assertThat(document.path("attributes").has("cibaInterval")).isTrue();
    }

    @Test
    void defaultsAreLeftOut() throws Exception {
        JsonNode document = document(extract(false));

        assertThat(clientIds(document))
                .doesNotContain(
                        "account",
                        "account-console",
                        "admin-cli",
                        "broker",
                        "realm-management",
                        "security-admin-console");
        assertThat(names(document.path("roles").path("realm"))).doesNotContain("uma_authorization");
    }

    @Test
    void noTypeIsDeclaredEmpty() throws Exception {
        JsonNode document = document(extract(false));

        document.properties().forEach(field -> {
            JsonNode value = field.getValue();
            if (value.isArray() || value.isObject()) {
                assertThat(value.isEmpty()).as(field.getKey()).isFalse();
            }
        });
    }

    @Test
    void secretsBecomeVariables() throws Exception {
        ExtractedRealm extracted = extract(false);
        JsonNode document = document(extracted);

        assertThat(byField(document.path("clients"), "clientId", "shop")
                        .path("secret")
                        .asText())
                .isEqualTo("{{shopSecret}}");
        assertThat(byField(document.path("identityProviders"), "alias", "google")
                        .path("config")
                        .path("clientSecret")
                        .asText())
                .isEqualTo("{{googleClientSecret}}");
        assertThat(document.path("smtpServer").path("password").asText()).isEqualTo("{{smtpPassword}}");
        assertThat(extracted.variables())
                .containsExactly(
                        "captchaActionApiKey",
                        "captchaActionSecret",
                        "captchaEnterpriseApiKey",
                        "captchaEnterpriseSecret",
                        "googleClientSecret",
                        "httpsSamlExampleOrgEncryptionPrivateKey",
                        "httpsSamlExampleOrgSecret",
                        "httpsSamlExampleOrgSigningPrivateKey",
                        "shopSecret",
                        "smtpPassword");
        JsonNode saml = byField(document.path("clients"), "clientId", "https://saml.example.org");
        assertThat(saml.path("attributes").path("saml.signing.private.key").asText())
                .isEqualTo("{{httpsSamlExampleOrgSigningPrivateKey}}");
        assertThat(saml.path("attributes").path("saml.encryption.private.key").asText())
                .isEqualTo("{{httpsSamlExampleOrgEncryptionPrivateKey}}");
    }

    @Test
    void noSecretValueAndNoMaskInTheDocument() {
        ExtractedRealm extracted = extract(true);

        assertThat(extracted.document())
                .doesNotContain(SHOP_SECRET, GOOGLE_SECRET, SMTP_PASSWORD, USER_PASSWORD, MASK)
                .doesNotContain(SIGNING_KEY, ENCRYPTION_KEY)
                .doesNotContain(CAPTCHA_SECRET, CAPTCHA_API_KEY)
                .doesNotContain(samlKey("saml.signing.private.key"), samlKey("saml.encryption.private.key"));
        assertThat(extracted.toString()).doesNotContain(extracted.document()).doesNotContain("{{shopSecret}}");
    }

    @Test
    void usersOnlyWithTheOption() throws Exception {
        assertThat(document(extract(false)).has("users")).isFalse();

        ExtractedRealm extracted = extract(true);
        JsonNode users = document(extracted).path("users");

        assertThat(users.findValuesAsText("username")).containsExactly("alice");
        JsonNode alice = users.get(0);
        assertThat(alice.path("email").asText()).isEqualTo("alice@example.org");
        assertThat(alice.path("firstName").asText()).isEqualTo("Alice");
        assertThat(alice.path("enabled").asBoolean()).isTrue();
        assertThat(texts(alice.path("realmRoles"))).containsExactly("manager");
        assertThat(texts(alice.path("groups"))).containsExactly("/staff/admins");
        assertThat(extracted.skippedUsers()).isZero();
    }

    @Test
    void usersHaveNoCredentials() throws Exception {
        JsonNode alice = document(extract(true)).path("users").get(0);

        assertThat(alice.has("credentials")).isFalse();
        assertThat(alice.has("id")).isFalse();
        assertThat(alice.has("createdTimestamp")).isFalse();
        assertThat(alice.has("totp")).isFalse();
    }

    @Test
    void clientSecretsAreTheCurrentOnes() {
        try (Connection connection = connect()) {
            Map<String, String> secrets = connection.clientSecrets(REALM);

            // the SAML client is confidential too: Keycloak gave it a secret
            assertThat(secrets)
                    .containsEntry("shopSecret", SHOP_SECRET)
                    .containsOnlyKeys("httpsSamlExampleOrgSecret", "shopSecret");
            assertThat(secrets.toString()).doesNotContain(SHOP_SECRET);
        }
    }

    @Test
    void anUnknownRealmFailsWithCode4() {
        try (Connection connection = connect()) {
            Throwable extraction = catchThrowable(() -> connection.extract("no-such-realm", false));
            Throwable secrets = catchThrowable(() -> connection.clientSecrets("no-such-realm"));

            assertThat(extraction)
                    .isInstanceOf(RealmNotFoundOnTargetException.class)
                    .hasMessage("Realm 'no-such-realm' not found on local");
            assertThat(ExitCodeResolver.resolve(extraction).code()).isEqualTo(4);
            assertThat(secrets).isInstanceOf(RealmNotFoundOnTargetException.class);
        }
    }

    @Test
    void aDeletedDefaultIsWarned() {
        assertThat(extract(false).warnings())
                .contains("Default realm role 'offline_access' was deleted in Keycloak: an import may bring it back");
    }

    /** The extracted document, given its variables, changes nothing on the target it comes from. */
    @Test
    void aDryRunOfTheDocumentHasNoChange() {
        try (Connection connection = connect()) {
            String document = withValues(connection.extract(REALM, false).document());

            ImportReport report =
                    connection.dryRun(List.of(new ImportFile("target-extract-it", REALM + ".json", document)));

            assertThat(report.plan(REALM)).isEmpty();
            assertThat(report.notCompared(REALM))
                    .extracting(value -> value.field())
                    .contains("smtpServer.password", "config.clientSecret");
        }
    }

    @Test
    void aDryRunOfTheDocumentWithItsUsersHasNoChange() {
        try (Connection connection = connect()) {
            String document = withValues(connection.extract(REALM, true).document());

            ImportReport report =
                    connection.dryRun(List.of(new ImportFile("target-extract-it", REALM + ".json", document)));

            assertThat(report.plan(REALM)).isEmpty();
        }
    }

    @Test
    void theUserProfileIsExtractedWhenItIsNotTheDefaultOne() throws Exception {
        try (Connection connection = connect()) {
            JsonNode document =
                    JSON.readTree(connection.extract(PROFILE_REALM, false).document());

            assertThat(document.path("userProfile").path("attributes").findValuesAsText("name"))
                    .contains("username", "email", "department");
            assertThat(document.path("attributes").path("userProfileEnabled").asText())
                    .isEqualTo("true");
        }
    }

    /**
     * The extracted user profile is the current one; the only change left is the attribute {@code userProfileEnabled}
     * the import needs to manage the user profile, which a hand-made realm does not have (a known limit, see the
     * report of the task).
     */
    @Test
    void aDryRunOfAnExtractedUserProfileOnlyAddsTheAttributeOfTheImport() {
        try (Connection connection = connect()) {
            String document = connection.extract(PROFILE_REALM, false).document();

            ImportReport report =
                    connection.dryRun(List.of(new ImportFile("target-extract-it", PROFILE_REALM + ".json", document)));

            assertThat(report.plan(PROFILE_REALM))
                    .singleElement()
                    .satisfies(change -> assertThat(change.type()).isEqualTo(ResourceType.REALM_SETTINGS));
            assertThat(DryRunReport.of(report).toText())
                    .contains("~ Realm settings '" + PROFILE_REALM + "' (attributes)");
        }
    }

    @Test
    void theDefaultUserProfileIsNotExtracted() throws Exception {
        try (Connection connection = connect()) {
            JsonNode document =
                    JSON.readTree(connection.extract(DEFAULT_REALM, false).document());

            assertThat(document.has("userProfile")).isFalse();
            assertThat(document.path("attributes").has("userProfileEnabled")).isFalse();
        }
    }

    /**
     * A client with Authorization Services: the only change the dry run may show is its authorization (once seen, cause
     * not found, see the report of the task; documented as a limit). Usually none.
     */
    @Test
    void aDryRunOfAClientWithAuthorizationShowsAtMostItsAuthorization() {
        try (Connection connection = connect()) {
            String document = connection.extract(AUTHZ_REALM, false).document().replace("{{apiSecret}}", API_SECRET);

            ImportReport report =
                    connection.dryRun(List.of(new ImportFile("target-extract-it", AUTHZ_REALM + ".json", document)));

            assertThat(document).contains("authorizationSettings");
            assertThat(report.plan(AUTHZ_REALM))
                    .allSatisfy(change -> assertThat(change.type()).isEqualTo(ResourceType.CLIENT_AUTHORIZATION));
        }
    }

    /** The secrets of an authenticator config (reCAPTCHA) become variables; its other values stay as they are. */
    @Test
    void theSecretsOfAnAuthenticatorConfigBecomeVariables() throws Exception {
        JsonNode configs = document(extract(false)).path("authenticatorConfig");
        JsonNode captcha = byField(configs, "alias", "captcha-action");

        assertThat(captcha.path("config").path("secret").asText()).isEqualTo("{{captchaActionSecret}}");
        assertThat(captcha.path("config").path("api.key").asText()).isEqualTo("{{captchaActionApiKey}}");
        assertThat(captcha.path("config").path("site.key").asText()).isEqualTo("planted-site-key");
    }

    /** The reference of the version of the integration tests is the profile Keycloak gives a new realm. */
    @Test
    void theReferenceUserProfileIsTheOneOfANewRealm() throws Exception {
        try (Keycloak admin = admin()) {
            UPConfig byDefault =
                    admin.realm(DEFAULT_REALM).users().userProfile().getConfiguration();
            String version = admin.serverInfo().getInfo().getSystemInfo().getVersion();

            assertThat(new BaselineProvider(new ObjectMapper(), new NormalizationOptions(null, null))
                            .getUserProfile(version)
                            .map(JSON::valueToTree))
                    .contains(JSON.valueToTree(byDefault));
        }
    }

    /** A private key of the SAML client, as Keycloak keeps it (it makes its own signing key). */
    private static String samlKey(final String attribute) {
        try (Keycloak admin = admin()) {
            return admin.realm(REALM)
                    .clients()
                    .findByClientId("https://saml.example.org")
                    .getFirst()
                    .getAttributes()
                    .get(attribute);
        }
    }

    private static String samlSecret() {
        try (Keycloak admin = admin()) {
            String id = admin.realm(REALM)
                    .clients()
                    .findByClientId("https://saml.example.org")
                    .getFirst()
                    .getId();
            return admin.realm(REALM).clients().get(id).getSecret().getValue();
        }
    }

    private static String withValues(final String document) {
        return document.replace("{{shopSecret}}", SHOP_SECRET)
                .replace("{{googleClientSecret}}", GOOGLE_SECRET)
                .replace("{{smtpPassword}}", SMTP_PASSWORD)
                .replace("{{httpsSamlExampleOrgSecret}}", samlSecret())
                .replace("{{captchaActionSecret}}", CAPTCHA_SECRET)
                .replace("{{captchaActionApiKey}}", CAPTCHA_API_KEY)
                .replace("{{captchaEnterpriseSecret}}", CAPTCHA_SECRET)
                .replace("{{captchaEnterpriseApiKey}}", CAPTCHA_API_KEY)
                .replace("{{httpsSamlExampleOrgSigningPrivateKey}}", samlKey("saml.signing.private.key"))
                .replace("{{httpsSamlExampleOrgEncryptionPrivateKey}}", samlKey("saml.encryption.private.key"));
    }

    private static ExtractedRealm extract(final boolean withUsers) {
        try (Connection connection = connect()) {
            return connection.extract(REALM, withUsers);
        }
    }

    private static JsonNode document(final ExtractedRealm extracted) throws Exception {
        return JSON.readTree(extracted.document());
    }

    private static List<String> clientIds(final JsonNode document) {
        return document.path("clients").findValuesAsText("clientId");
    }

    private static List<String> names(final JsonNode elements) {
        List<String> names = new ArrayList<>();
        elements.forEach(element -> names.add(element.path("name").asText()));
        return names;
    }

    private static List<String> texts(final JsonNode elements) {
        List<String> texts = new ArrayList<>();
        elements.forEach(element -> texts.add(element.asText()));
        return texts;
    }

    private static JsonNode byName(final JsonNode elements, final String name) {
        return byField(elements, "name", name);
    }

    private static JsonNode byField(final JsonNode elements, final String field, final String value) {
        for (JsonNode element : elements) {
            if (value.equals(element.path(field).asText())) {
                return element;
            }
        }
        return null;
    }

    private static RoleRepresentation role(final String name) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        return role;
    }

    private static Connection connect() {
        return new ChildContextKeycloakConnector(mainEnvironment, ShellAdaptersConfiguration.defaultImportOptions())
                .connect(passwordTarget(), ADMIN_PASSWORD);
    }

    private static Target passwordTarget() {
        return new Target(
                "local",
                URL,
                "master",
                "admin-cli",
                GrantType.PASSWORD,
                Optional.of(ADMIN),
                true,
                Duration.ofSeconds(5));
    }

    private static Keycloak admin() {
        return KeycloakBuilder.builder()
                .serverUrl(URL)
                .realm("master")
                .clientId("admin-cli")
                .username(ADMIN)
                .password(ADMIN_PASSWORD)
                .build();
    }

    /** A private key, encoded as Keycloak keeps the keys of a SAML client. */
    private static String privateKey() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return java.util.Base64.getEncoder()
                    .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
