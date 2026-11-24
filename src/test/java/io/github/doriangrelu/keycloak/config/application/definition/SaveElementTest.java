package io.github.doriangrelu.keycloak.config.application.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import java.util.List;
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

        assertThatThrownBy(() -> new SaveElement(definitions).save("demo", READER, Optional.empty(), false))
                .isInstanceOf(ReadOnlyRealmException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void anUnformattedTargetFileIsNotChanged() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.ROLE), List.of()));
        definitions.files.put(ElementType.ROLE, "30-roles.yaml");
        definitions.unformatted.add("30-roles.yaml");

        assertThatThrownBy(() -> new SaveElement(definitions).save("demo", READER, Optional.empty(), false))
                .isInstanceOf(UnformattedFileException.class)
                .hasMessage("Run 'realm format' first: 30-roles.yaml");
        assertThatThrownBy(() -> new SaveElement(definitions)
                        .save("demo", READER, Optional.of(new Origin("30-roles.yaml", 3)), false))
                .isInstanceOf(UnformattedFileException.class);
        assertThat(definitions.saved).isEmpty();
    }

    @Test
    void aFirstDeclarationOfATypeNeedsAnExplicitConfirmation() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.CLIENT), List.of()));
        SaveElement saveElement = new SaveElement(definitions);

        assertThatThrownBy(() -> saveElement.save("demo", READER, Optional.empty(), false))
                .isInstanceOf(FirstDeclarationException.class)
                .hasMessage("At the next import, all the roles of Keycloak missing from the files will be deleted."
                        + " Run a dry run first, then confirm.");
        assertThat(definitions.saved).isEmpty();

        assertThat(saveElement.save("demo", READER, Optional.empty(), true)).isEqualTo("30-roles.yaml");
        assertThat(definitions.saved).containsExactly(READER);
    }

    @Test
    void anElementOfADeclaredTypeIsSaved() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(Set.of(ElementType.ROLE), List.of()));
        definitions.files.put(ElementType.ROLE, "30-roles.yaml");

        assertThat(new SaveElement(definitions).save("demo", READER, Optional.empty(), false))
                .isEqualTo("30-roles.yaml");
    }
}
