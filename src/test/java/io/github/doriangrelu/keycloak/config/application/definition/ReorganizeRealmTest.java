package io.github.doriangrelu.keycloak.config.application.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReorganizeRealmTest {

    private static RealmDefinition realm(final List<UnreadableFile> unreadable) {
        return new RealmDefinition("demo", Optional.empty(), List.of(), List.of(), List.of(), Set.of(), unreadable);
    }

    private static ReorganizationPlan aPlan() {
        return new ReorganizationPlan(
                List.of(new ReorganizationPlan.Move("roles", OptionalInt.of(2), "00-realm.yaml", "30-roles.yaml")),
                List.of(new ReorganizationPlan.Rename("realm.yaml", "00-realm.yaml")),
                List.of("empty.yaml"));
    }

    @Test
    void anUnknownFolderIsRefused() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(List.of()));

        assertThatThrownBy(() -> new ReorganizeRealm(definitions).plan("other"))
                .isInstanceOf(UnknownRealmFolderException.class);
    }

    @Test
    void anUnformattedFileIsRefused() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(List.of()));
        definitions.unformatted.add("30-roles.yaml");
        definitions.unformatted.add("40-groups.yaml");

        assertThatThrownBy(() -> new ReorganizeRealm(definitions).plan("demo"))
                .isInstanceOf(UnformattedFileException.class)
                .hasMessage("Run 'realm format' first: 30-roles.yaml");
        assertThatThrownBy(() -> new ReorganizeRealm(definitions).apply("demo", aPlan()))
                .isInstanceOf(UnformattedFileException.class);
        assertThat(definitions.reorganized).isEmpty();
    }

    @Test
    void anUnreadableFileIsRefused() {
        FakeRealmDefinitions definitions =
                new FakeRealmDefinitions(realm(List.of(new UnreadableFile("00-realm.yaml", 2, "unquoted"))));

        assertThatThrownBy(() -> new ReorganizeRealm(definitions).plan("demo"))
                .isInstanceOf(ReadOnlyRealmException.class);
        assertThatThrownBy(() -> new ReorganizeRealm(definitions).apply("demo", aPlan()))
                .isInstanceOf(ReadOnlyRealmException.class);
        assertThat(definitions.reorganized).isEmpty();
    }

    @Test
    void thePlanComesFromThePort() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(List.of()));
        definitions.plan = aPlan();

        assertThat(new ReorganizeRealm(definitions).plan("demo")).isEqualTo(aPlan());
        assertThat(definitions.reorganized).isEmpty();
    }

    @Test
    void applyWritesThroughThePort() {
        FakeRealmDefinitions definitions = new FakeRealmDefinitions(realm(List.of()));
        definitions.written = List.of("30-roles.yaml", "00-realm.yaml");

        assertThat(new ReorganizeRealm(definitions).apply("demo", aPlan()))
                .containsExactly("30-roles.yaml", "00-realm.yaml");
        assertThat(definitions.reorganized).containsExactly(aPlan());
    }

    @Test
    void aPlanWithNothingToDoIsEmpty() {
        assertThat(new ReorganizationPlan(List.of(), List.of(), List.of()).isEmpty())
                .isTrue();
        assertThat(aPlan().isEmpty()).isFalse();
    }
}
