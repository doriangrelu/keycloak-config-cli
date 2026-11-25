package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SaveTest {

    private static final String REALM = "realm: \"{{env}}-mon-realm\"\n";

    @TempDir
    Path root;

    private Path folder;
    private RealmDefinitionFiles files;

    @BeforeEach
    void setUp() throws IOException {
        this.folder = Files.createDirectories(this.root.resolve("demo"));
        this.files = new RealmDefinitionFiles(this.root);
    }

    private void write(final String name, final String content) throws IOException {
        Files.writeString(this.folder.resolve(name), content, StandardCharsets.UTF_8);
    }

    private String read(final String name) throws IOException {
        return Files.readString(this.folder.resolve(name), StandardCharsets.UTF_8);
    }

    private RealmDefinition realm() {
        return this.files.load("demo").orElseThrow();
    }

    private static ClientDefinition client(final String id, final ApplicationType type, final String... redirectUris) {
        return new ClientDefinition(
                TemplatedValue.of(id),
                Optional.empty(),
                Optional.empty(),
                type,
                List.of(redirectUris).stream().map(TemplatedValue::of).toList(),
                List.of(),
                Map.of(),
                Optional.empty());
    }

    @Test
    void editingAClientKeepsWhatTheModelDoesNotManage() throws IOException {
        write("20-clients.yaml", REALM + """
                clients:
                  # Reviewed with the security team
                  - clientId: frontend
                    description: Old description
                    publicClient: true # a SPA cannot keep a secret
                    standardFlowEnabled: true
                    implicitFlowEnabled: false
                    directAccessGrantsEnabled: false
                    serviceAccountsEnabled: false
                    redirectUris:
                      - "{{appUrl}}/*"
                    attributes:
                      custom.key: kept
                    protocolMappers:
                      - name: audience
                        protocolMapper: oidc-audience-mapper
                """);
        this.files.format("demo");
        List<String> before = read("20-clients.yaml").lines().toList();
        Defined<ClientDefinition> frontend = realm().client("frontend").orElseThrow();
        ClientDefinition changed = new ClientDefinition(
                frontend.definition().clientId(),
                frontend.definition().name(),
                Optional.of(TemplatedValue.of("New description")),
                frontend.definition().applicationType(),
                frontend.definition().redirectUris(),
                frontend.definition().webOrigins(),
                frontend.definition().attributes(),
                frontend.definition().secret());

        assertThat(this.files.save("demo", changed, Optional.of(frontend.origin()), ElementKeys.empty()))
                .isEqualTo("20-clients.yaml");

        String after = read("20-clients.yaml");
        assertThat(after)
                .contains("protocolMappers", "oidc-audience-mapper", "custom.key: kept")
                .contains("# Reviewed with the security team", "# a SPA cannot keep a secret");
        List<String> removed = new ArrayList<>(before);
        removed.removeAll(after.lines().toList());
        List<String> added = new ArrayList<>(after.lines().toList());
        added.removeAll(before);
        assertThat(removed).singleElement().asString().contains("Old description");
        assertThat(added).singleElement().asString().contains("New description");
    }

    @Test
    void aNewClientGoesToTheFileThatHoldsTheClients() throws IOException {
        write("00-realm.yaml", REALM + "enabled: true\n");
        write("20-clients.yaml", REALM + "clients:\n  - clientId: frontend\n");

        String file = this.files.save(
                "demo", client("batch", ApplicationType.SERVICE), Optional.empty(), ElementKeys.empty());

        assertThat(file).isEqualTo("20-clients.yaml");
        assertThat(realm().clients())
                .extracting(defined -> defined.definition().clientId().raw())
                .containsExactly("frontend", "batch");
        assertThat(realm().client("batch").orElseThrow().definition().applicationType())
                .isEqualTo(ApplicationType.SERVICE);
        assertThat(read("00-realm.yaml")).doesNotContain("batch");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void aFirstRoleCreatesTheNumberedFile(final String extension) throws IOException {
        write("00-realm." + extension, extension.equals("yaml") ? REALM : "{\n  \"realm\": \"{{env}}-mon-realm\"\n}\n");

        String file = this.files.save(
                "demo",
                new RoleDefinition(TemplatedValue.of("reader"), Optional.empty(), Optional.empty()),
                Optional.empty(),
                ElementKeys.empty());

        assertThat(file).isEqualTo("30-roles." + extension);
        assertThat(read(file)).contains("{{env}}-mon-realm");
        assertThat(realm().roles())
                .extracting(role -> role.definition().name().raw())
                .containsExactly("reader");
        assertThat(realm().roles().getFirst().origin().file()).isEqualTo(file);
    }

    @Test
    void aClientRoleGoesUnderItsClient() throws IOException {
        write("30-roles.yaml", REALM + "roles:\n  realm:\n    - name: reader\n");

        this.files.save(
                "demo",
                new RoleDefinition(
                        TemplatedValue.of("viewer"), Optional.of(TemplatedValue.of("frontend")), Optional.empty()),
                Optional.empty(),
                ElementKeys.empty());

        assertThat(realm().roles())
                .extracting(
                        role -> role.definition().name().raw(),
                        role -> role.definition().clientId())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("reader", Optional.empty()),
                        org.assertj.core.groups.Tuple.tuple("viewer", Optional.of(TemplatedValue.of("frontend"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void placeholdersAreWrittenQuoted(final String extension) throws IOException {
        write("00-realm." + extension, extension.equals("yaml") ? REALM : "{\"realm\": \"{{env}}-mon-realm\"}");

        String file = this.files.save(
                "demo", client("frontend", ApplicationType.SPA, "{{appUrl}}/*"), Optional.empty(), ElementKeys.empty());

        assertThat(read(file)).contains("\"{{appUrl}}/*\"");
        assertThat(realm().client("frontend").orElseThrow().definition().redirectUris())
                .containsExactly(TemplatedValue.of("{{appUrl}}/*"));
    }

    @Test
    void changingTheTypeRewritesTheFlows() throws IOException {
        write(
                "20-clients.yaml",
                REALM + "clients:\n  - clientId: app\n    publicClient: true\n"
                        + "    directAccessGrantsEnabled: false\n");
        Defined<ClientDefinition> app = realm().client("app").orElseThrow();
        assertThat(app.definition().applicationType()).isEqualTo(ApplicationType.SPA);

        this.files.save(
                "demo", client("app", ApplicationType.WEB_APPLICATION), Optional.of(app.origin()), ElementKeys.empty());

        assertThat(realm().client("app").orElseThrow().definition().applicationType())
                .isEqualTo(ApplicationType.WEB_APPLICATION);
        assertThat(read("20-clients.yaml")).contains("publicClient: false").doesNotContain("bearerOnly");
    }

    @Test
    void aSubGroupIsWrittenUnderItsParent() throws IOException {
        write("40-groups.yaml", REALM + "groups:\n  - name: team\n    realmRoles:\n      - reader\n");

        this.files.save(
                "demo",
                new GroupDefinition(
                        "/team/devs", TemplatedValue.of("devs"), Set.of(), Map.of("frontend", Set.of("viewer"))),
                Optional.empty(),
                ElementKeys.empty());

        assertThat(realm().groups())
                .extracting(group -> group.definition().path())
                .containsExactly("/team", "/team/devs");
        assertThat(realm().groups().get(0).definition().realmRoles()).containsExactly("reader");
        assertThat(realm().groups().get(1).definition().clientRoles()).containsEntry("frontend", Set.of("viewer"));
    }

    @Test
    void twoElementsOnOneLineAreToldApart() throws IOException {
        write("30-roles.yaml", REALM + "roles:\n  realm: [{name: a}, {name: b}]\n");
        Defined<RoleDefinition> b = realm().roles().get(1);

        this.files.save(
                "demo",
                new RoleDefinition(TemplatedValue.of("b"), Optional.empty(), Optional.of(TemplatedValue.of("Second"))),
                Optional.of(b.origin()),
                ElementKeys.empty());

        assertThat(realm().roles())
                .extracting(
                        role -> role.definition().name().raw(),
                        role -> role.definition().description())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", Optional.empty()),
                        org.assertj.core.groups.Tuple.tuple("b", Optional.of(TemplatedValue.of("Second"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void aTemplatedFlowMakesTheClientCustomAndIsNeverRewritten(final String extension) throws IOException {
        String name = "20-clients." + extension;
        write(
                name,
                extension.equals("yaml")
                        // Read with the defaults of Keycloak, these flows would make a web application
                        ? REALM + "clients:\n  - clientId: app\n    publicClient: \"{{public}}\"\n"
                                + "    directAccessGrantsEnabled: false\n"
                        : "{\"realm\": \"x\", \"clients\": [{\"clientId\": \"app\", \"publicClient\": \"{{public}}\","
                                + " \"directAccessGrantsEnabled\": false}]}");
        this.files.format("demo");
        Defined<ClientDefinition> app = realm().client("app").orElseThrow();
        assertThat(app.definition().applicationType()).isEqualTo(ApplicationType.CUSTOM);

        this.files.save(
                "demo",
                new ClientDefinition(
                        app.definition().clientId(),
                        Optional.empty(),
                        Optional.of(TemplatedValue.of("Described")),
                        ApplicationType.CUSTOM,
                        List.of(),
                        List.of(),
                        Map.of(),
                        Optional.empty()),
                Optional.of(app.origin()),
                ElementKeys.empty());

        assertThat(read(name)).contains("{{public}}").contains("Described");
    }

    @Test
    void editingTheDescriptionOfACustomClientKeepsItsFlows() throws IOException {
        // Public, with the direct access grants and the implicit flow: no application type has these flows
        write("20-clients.yaml", REALM + """
                clients:
                  - clientId: legacy
                    description: Before
                    publicClient: true
                    standardFlowEnabled: true
                    implicitFlowEnabled: true
                    directAccessGrantsEnabled: true
                    serviceAccountsEnabled: false
                    redirectUris:
                      - "{{appUrl}}/*"
                    attributes:
                      custom.key: kept
                """);
        this.files.format("demo");
        List<String> before = read("20-clients.yaml").lines().toList();
        Defined<ClientDefinition> legacy = realm().client("legacy").orElseThrow();
        assertThat(legacy.definition().applicationType()).isEqualTo(ApplicationType.CUSTOM);
        ClientDefinition definition = legacy.definition();

        this.files.save(
                "demo",
                new ClientDefinition(
                        definition.clientId(),
                        definition.name(),
                        Optional.of(TemplatedValue.of("After")),
                        definition.applicationType(),
                        definition.redirectUris(),
                        definition.webOrigins(),
                        definition.attributes(),
                        definition.secret()),
                Optional.of(legacy.origin()),
                ElementKeys.empty());

        List<String> after = read("20-clients.yaml").lines().toList();
        List<String> removed = new ArrayList<>(before);
        removed.removeAll(after);
        List<String> added = new ArrayList<>(after);
        added.removeAll(before);
        assertThat(removed).singleElement().asString().contains("Before");
        assertThat(added).singleElement().asString().contains("After");
        assertThat(after).hasSameSizeAs(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void unchangedNumbersAndBooleansAreNotRewritten(final String extension) throws IOException {
        String name = "20-clients." + extension;
        write(
                name,
                extension.equals("yaml")
                        ? REALM + "clients:\n  - clientId: app\n    description: Before\n    publicClient: true\n"
                                + "    directAccessGrantsEnabled: false\n    attributes:\n"
                                + "      access.token.lifespan: 300\n      use.refresh.tokens: true\n"
                        : "{\"realm\": \"x\", \"clients\": [{\"clientId\": \"app\", \"description\": \"Before\","
                                + " \"publicClient\": true, \"directAccessGrantsEnabled\": false, \"attributes\":"
                                + " {\"access.token.lifespan\": 300, \"use.refresh.tokens\": \"true\"}}]}");
        this.files.format("demo");
        List<String> before = read(name).lines().toList();
        Defined<ClientDefinition> app = realm().client("app").orElseThrow();

        this.files.save(
                "demo",
                new ClientDefinition(
                        app.definition().clientId(),
                        app.definition().name(),
                        Optional.of(TemplatedValue.of("After")),
                        app.definition().applicationType(),
                        app.definition().redirectUris(),
                        app.definition().webOrigins(),
                        app.definition().attributes(),
                        app.definition().secret()),
                Optional.of(app.origin()),
                ElementKeys.empty());

        List<String> removed = new ArrayList<>(before);
        removed.removeAll(read(name).lines().toList());
        assertThat(removed).singleElement().asString().contains("Before");
    }

    @Test
    void theFileOfATypeIsTheFirstThatDeclaresIt() throws IOException {
        write("00-realm.yaml", REALM);
        write("20-clients.yaml", REALM + "clients: []\n");

        assertThat(this.files.fileOf(
                        "demo", io.github.doriangrelu.keycloak.config.domain.definition.ElementType.CLIENT))
                .contains("20-clients.yaml");
        assertThat(this.files.fileOf("demo", io.github.doriangrelu.keycloak.config.domain.definition.ElementType.GROUP))
                .isEmpty();
    }

    private static ElementKeys added(final String path, final KeyValue value) {
        return new ElementKeys(Map.of(KeyPath.parse(path), value));
    }

    private static KeyValue scalar(final String text) {
        return new KeyValue.Scalar(TemplatedValue.of(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void aBooleanDefaultIsWrittenAsABoolean(final String extension) throws IOException {
        String name = "20-clients." + extension;
        write(name, extension.equals("yaml") ? REALM + "clients: []\n" : "{\"realm\": \"x\", \"clients\": []}");

        this.files.save(
                "demo",
                client("app", ApplicationType.SERVICE),
                Optional.empty(),
                added("frontchannelLogout", scalar("true")));

        assertThat(read(name))
                .containsPattern("frontchannelLogout\"?: true")
                .doesNotContain("\"true\"")
                .doesNotContain("'true'");
    }

    @Test
    void anAddedKeyNeverOverwritesAWrittenOne() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n    frontchannelLogout: false\n");
        Defined<ClientDefinition> app = realm().client("app").orElseThrow();

        this.files.save(
                "demo",
                app.definition(),
                Optional.of(app.origin()),
                new ElementKeys(Map.of(
                        KeyPath.parse("frontchannelLogout"), scalar("true"),
                        KeyPath.parse("consentRequired"), scalar("false"))));

        assertThat(read("20-clients.yaml"))
                .contains("frontchannelLogout: false")
                .doesNotContain("frontchannelLogout: true")
                .contains("consentRequired: false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void aNestedAddedKeyCreatesItsParents(final String extension) throws IOException {
        String name = "20-clients." + extension;
        write(name, extension.equals("yaml") ? REALM + "clients: []\n" : "{\"realm\": \"x\", \"clients\": []}");

        this.files.save(
                "demo",
                client("app", ApplicationType.SERVICE),
                Optional.empty(),
                added("attributes.\"pkce.code.challenge.method\"", scalar("S256")));

        assertThat(realm().client("app").orElseThrow().definition().attributes())
                .containsEntry("pkce.code.challenge.method", TemplatedValue.of("S256"));
    }

    @Test
    void anAddedListIsWrittenAsAList() throws IOException {
        write("20-clients.yaml", REALM + "clients: []\n");

        this.files.save(
                "demo",
                client("app", ApplicationType.SERVICE),
                Optional.empty(),
                added("webOrigins", new KeyValue.Listed(List.of(TemplatedValue.of("+")))));

        assertThat(realm().client("app").orElseThrow().definition().webOrigins())
                .containsExactly(TemplatedValue.of("+"));
    }

    @Test
    void previewWritesNothing() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n");
        String before = read("20-clients.yaml");

        ElementKeys view = this.files.preview(
                "demo",
                client("batch", ApplicationType.SERVICE),
                Optional.empty(),
                added("attributes.\"pkce.code.challenge.method\"", scalar("S256")));

        assertThat(read("20-clients.yaml")).isEqualTo(before);
        assertThat(view.get(KeyPath.parse("attributes.\"pkce.code.challenge.method\"")))
                .contains(scalar("S256"));
        assertThat(view.get(KeyPath.parse("clientId"))).contains(scalar("batch"));
    }

    @Test
    void previewOfAFirstElementWorksOnANewDocument() throws IOException {
        write("00-realm.yaml", REALM);

        ElementKeys view = this.files.preview(
                "demo",
                new RoleDefinition(TemplatedValue.of("reader"), Optional.empty(), Optional.empty()),
                Optional.empty(),
                ElementKeys.empty());

        assertThat(view.get(KeyPath.parse("name"))).contains(scalar("reader"));
        assertThat(this.folder.resolve("30-roles.yaml")).doesNotExist();
    }

    @Test
    void aSecretVariableIsReadAndWrittenQuoted() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n    secret: \"{{apiSecret}}\"\n");
        this.files.format("demo");
        Defined<ClientDefinition> app = realm().client("app").orElseThrow();
        assertThat(app.definition().secret()).contains(TemplatedValue.of("{{apiSecret}}"));

        this.files.save(
                "demo",
                new ClientDefinition(
                        app.definition().clientId(),
                        app.definition().name(),
                        Optional.of(TemplatedValue.of("Described")),
                        app.definition().applicationType(),
                        app.definition().redirectUris(),
                        app.definition().webOrigins(),
                        app.definition().attributes(),
                        app.definition().secret()),
                Optional.of(app.origin()),
                ElementKeys.empty());

        assertThat(read("20-clients.yaml")).contains("secret: \"{{apiSecret}}\"");
    }

    @Test
    void noSecretRemovesTheKey() throws IOException {
        write("20-clients.yaml", REALM + "clients:\n  - clientId: app\n    secret: \"{{apiSecret}}\"\n");
        this.files.format("demo");
        Defined<ClientDefinition> app = realm().client("app").orElseThrow();

        this.files.save(
                "demo",
                new ClientDefinition(
                        app.definition().clientId(),
                        app.definition().name(),
                        app.definition().description(),
                        app.definition().applicationType(),
                        app.definition().redirectUris(),
                        app.definition().webOrigins(),
                        app.definition().attributes(),
                        Optional.empty()),
                Optional.of(app.origin()),
                ElementKeys.empty());

        assertThat(read("20-clients.yaml")).doesNotContain("secret");
    }
}
