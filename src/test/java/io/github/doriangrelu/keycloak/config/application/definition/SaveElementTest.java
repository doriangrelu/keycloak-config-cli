package io.github.doriangrelu.keycloak.config.application.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SaveElementTest {

    private static final RoleDefinition READER =
            new RoleDefinition(TemplatedValue.of("reader"), Optional.empty(), Optional.empty());

    private static RealmDefinition realm(final Set<ElementType> declared, final List<UnreadableFile> unreadable) {
        return new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), declared, unreadable);
    }

    @Test
    void aReadOnlyRealmIsNotChanged() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(
                realm(Set.of(ElementType.ROLE), List.of(new UnreadableFile("00-realm.yaml", 2, "unquoted"))));

        assertThatThrownBy(
                        () -> new SaveElement(definitions, Rules.none()).save("demo", READER, Optional.empty(), false))
                .isInstanceOf(ReadOnlyRealmException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void anUnformattedTargetFileIsNotChanged() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.ROLE), List.of()));
        definitions.files.put(ElementType.ROLE, "30-roles.yaml");
        definitions.unformatted.add("30-roles.yaml");

        assertThatThrownBy(
                        () -> new SaveElement(definitions, Rules.none()).save("demo", READER, Optional.empty(), false))
                .isInstanceOf(UnformattedFileException.class)
                .hasMessage("Run 'realm format' first: 30-roles.yaml");
        assertThatThrownBy(() -> new SaveElement(definitions, Rules.none())
                        .save("demo", READER, Optional.of(new Origin("30-roles.yaml", 3)), false))
                .isInstanceOf(UnformattedFileException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void aFirstDeclarationOfATypeNeedsAnExplicitConfirmation() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.CLIENT), List.of()));
        SaveElement saveElement = new SaveElement(definitions, Rules.none());

        assertThatThrownBy(() -> saveElement.save("demo", READER, Optional.empty(), false))
                .isInstanceOf(FirstDeclarationException.class)
                .hasMessage("At the next import, all the realm roles of Keycloak missing from the files will be"
                        + " deleted. Run a dry run first, then confirm.");
        assertThat(definitions.saved).isEmpty();

        assertThat(saveElement.save("demo", READER, Optional.empty(), true).file())
                .isEqualTo("30-roles.yaml");
        assertThat(definitions.saved).containsExactly(READER);
    }

    /** The import cleans the realm roles and the roles of each client apart (ADR-007). */
    @Test
    void theFirstRoleOfAClientNeedsAConfirmationEvenWhenRealmRolesAreDeclared() {
        RealmDefinition realm = new RealmDefinition(
                "demo",
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(ElementType.ROLE),
                true,
                Set.of(),
                List.of());
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm);
        RoleDefinition viewer = new RoleDefinition(
                TemplatedValue.of("viewer"), Optional.of(TemplatedValue.of("frontend")), Optional.empty());

        assertThatThrownBy(
                        () -> new SaveElement(definitions, Rules.none()).save("demo", viewer, Optional.empty(), false))
                .isInstanceOf(FirstDeclarationException.class)
                .hasMessageContaining("all the roles of client 'frontend' of Keycloak");
        assertThat(new SaveElement(definitions, Rules.none())
                        .save("demo", READER, Optional.empty(), false)
                        .file())
                .isEqualTo("30-roles.yaml");
    }

    @Test
    void anElementOfADeclaredTypeIsSaved() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.ROLE), List.of()));
        definitions.files.put(ElementType.ROLE, "30-roles.yaml");

        assertThat(new SaveElement(definitions, Rules.none())
                        .save("demo", READER, Optional.empty(), false)
                        .file())
                .isEqualTo("30-roles.yaml");
    }

    private static final Origin HERE = new Origin("20-clients.yaml", 3);

    private static ClientDefinition client(final ApplicationType type) {
        return new ClientDefinition(
                TemplatedValue.of("app"),
                Optional.empty(),
                Optional.empty(),
                type,
                List.of(),
                List.of(),
                Map.of(),
                Optional.empty());
    }

    private static FakeRealmDefinitions clientRealm() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.CLIENT), List.of()));
        definitions.files.put(ElementType.CLIENT, "20-clients.yaml");
        return definitions;
    }

    @Test
    void aNewSpaGetsPkceAndIsWritten() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.previewed = Rules.client(ApplicationType.SPA);

        SaveResult result = new SaveElement(definitions, Rules.of(Rules.SPA_PKCE))
                .save("demo", client(ApplicationType.SPA), Optional.empty(), false);

        assertThat(definitions.savedKeys).containsExactly(Rules.keys("pkce", "S256"));
        assertThat(result.verdict().allowsWriting()).isTrue();
        assertThat(result.file()).isEqualTo("20-clients.yaml");
    }

    @Test
    void aNewClientWithAWildcardIsRefusedAndNothingIsWritten() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.previewed = Rules.withList(Rules.client(ApplicationType.API), "redirectUris", "https://*");

        assertThatThrownBy(() -> new SaveElement(definitions, Rules.of(Rules.NO_WILDCARD))
                        .save("demo", client(ApplicationType.API), Optional.empty(), false))
                .isInstanceOf(RuleViolationException.class)
                .hasMessage("The rules refuse this change:\n- client.no-wildcard: A redirect URI has no wildcard.");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void editingTheDescriptionOfAHandWrittenWildcardClientIsWritten() {
        FakeRealmDefinitions definitions = clientRealm();
        ElementKeys wildcard = Rules.withList(Rules.client(ApplicationType.API), "redirectUris", "https://*");
        definitions.elementKeys.put(HERE, wildcard);
        definitions.previewed = wildcard;

        SaveResult result = new SaveElement(definitions, Rules.of(Rules.NO_WILDCARD))
                .save("demo", client(ApplicationType.API), Optional.of(HERE), false);

        assertThat(definitions.saved).hasSize(1);
        assertThat(result.verdict().reminders()).hasSize(1);
    }

    @Test
    void removingPkceFromASpaIsRefused() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.elementKeys.put(HERE, Rules.client(ApplicationType.SPA, "pkce", "S256"));
        definitions.previewed = Rules.client(ApplicationType.SPA);

        assertThatThrownBy(() -> new SaveElement(definitions, Rules.of(Rules.SPA_PKCE))
                        .save("demo", client(ApplicationType.SPA), Optional.of(HERE), false))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("- client.spa.pkce: A SPA uses PKCE.");
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void changingTheTypeToSpaAddsPkce() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.elementKeys.put(HERE, Rules.client(ApplicationType.API));
        definitions.previewed = Rules.client(ApplicationType.SPA);

        new SaveElement(definitions, Rules.of(Rules.SPA_PKCE))
                .save("demo", client(ApplicationType.SPA), Optional.of(HERE), false);

        assertThat(definitions.savedKeys).containsExactly(Rules.keys("pkce", "S256"));
    }

    @Test
    void anExistingClientKeepsItsKeysWhenTheTypeStaysTheSame() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.elementKeys.put(HERE, Rules.client(ApplicationType.SPA, "pkce", "S256"));
        definitions.previewed = Rules.client(ApplicationType.SPA, "pkce", "S256");

        new SaveElement(definitions, Rules.of(Rules.SPA_PKCE))
                .save("demo", client(ApplicationType.SPA), Optional.of(HERE), false);

        assertThat(definitions.savedKeys).containsExactly(ElementKeys.empty());
    }

    @Test
    void anAdviceIsReturnedAndWritten() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.previewed = Rules.client(ApplicationType.API);

        SaveResult result = new SaveElement(definitions, Rules.of(Rules.DESCRIBED))
                .save("demo", client(ApplicationType.API), Optional.empty(), false);

        assertThat(result.verdict().advice()).hasSize(1);
        assertThat(definitions.saved).hasSize(1);
    }

    @Test
    void theDefaultsSetByTheRulesAreReturned() {
        FakeRealmDefinitions definitions = clientRealm();
        definitions.previewed = Rules.client(ApplicationType.SPA);

        SaveResult result = new SaveElement(definitions, Rules.of(Rules.SPA_PKCE))
                .save("demo", client(ApplicationType.SPA), Optional.empty(), false);

        assertThat(result.defaults()).isEqualTo(Rules.keys("pkce", "S256"));
    }
}
