package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One realm in memory, recording what the use cases ask the port to write. */
final class FakeRealmDefinitions implements RealmDefinitions {

    final RealmDefinition realm;
    final List<String> unformatted = new ArrayList<>();
    final Map<ElementType, String> files = new EnumMap<>(ElementType.class);
    final List<String> formatted = new ArrayList<>();
    final List<ElementDefinition> saved = new ArrayList<>();
    final List<String> created = new ArrayList<>();

    FakeRealmDefinitions(final RealmDefinition realm) {
        this.realm = realm;
    }

    @Override
    public List<String> folders() {
        return List.of(this.realm.folder());
    }

    @Override
    public Optional<RealmDefinition> load(final String folder) {
        return Optional.of(this.realm).filter(found -> found.folder().equals(folder));
    }

    @Override
    public List<String> unformattedFiles(final String folder) {
        return List.copyOf(this.unformatted);
    }

    @Override
    public List<String> format(final String folder) {
        this.formatted.add(folder);
        return List.copyOf(this.unformatted);
    }

    @Override
    public Map<String, String> sources(final String folder) {
        return Map.of();
    }

    @Override
    public Optional<String> fileOf(final String folder, final ElementType type) {
        return Optional.ofNullable(this.files.get(type));
    }

    @Override
    public String save(final String folder, final ElementDefinition definition, final Optional<Origin> existing) {
        this.saved.add(definition);
        return existing.map(Origin::file).orElse(definition.type().fileName() + ".yaml");
    }

    @Override
    public String createRealm(final String folder, final TemplatedValue realm, final DefinitionFormat format) {
        this.created.add(folder);
        return "00-realm" + format.extension();
    }
}
