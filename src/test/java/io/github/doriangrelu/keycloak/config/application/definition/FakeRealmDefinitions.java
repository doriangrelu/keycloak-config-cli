package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One realm in memory, recording what the use cases ask the port to write. */
final class FakeRealmDefinitions implements RealmDefinitions {

    final RealmDefinition realm;
    final List<ImportFile> importFiles = new ArrayList<>();
    final List<ElementDefinition> importedWithElement = new ArrayList<>();
    Map<String, String> importedWith = Map.of();
    final List<String> unformatted = new ArrayList<>();
    final Map<ElementType, String> files = new EnumMap<>(ElementType.class);
    final List<String> formatted = new ArrayList<>();
    final List<ElementDefinition> saved = new ArrayList<>();
    final List<String> created = new ArrayList<>();
    final List<ElementKeys> savedKeys = new ArrayList<>();
    final List<ElementKeys> createdKeys = new ArrayList<>();
    final Map<Origin, ElementKeys> elementKeys = new LinkedHashMap<>();
    ElementKeys realmKeys = ElementKeys.empty();
    ElementKeys previewed = ElementKeys.empty();
    ReorganizationPlan plan = new ReorganizationPlan(List.of(), List.of(), List.of());
    List<String> written = List.of();
    Map<String, String> sources = Map.of();
    /** The sources with the element written; the sources when not given. */
    Map<String, String> sourcesWith;

    final List<ReorganizationPlan> reorganized = new ArrayList<>();

    FakeRealmDefinitions(final RealmDefinition realm) {
        this.realm = realm;
    }

    @Override
    public List<ImportFile> importFiles(final String folder, final Map<String, String> values) {
        this.importedWith = Map.copyOf(values);
        return List.copyOf(this.importFiles);
    }

    @Override
    public List<ImportFile> importFilesWith(
            final String folder, final ElementDefinition element, final Map<String, String> values) {
        this.importedWithElement.add(element);
        this.importedWith = Map.copyOf(values);
        return List.copyOf(this.importFiles);
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
    public Map<String, String> sourcesWith(final String folder, final ElementDefinition element) {
        return this.sourcesWith == null ? this.sources : this.sourcesWith;
    }

    @Override
    public Map<String, String> sources(final String folder) {
        return this.sources;
    }

    @Override
    public Optional<String> fileOf(final String folder, final ElementType type) {
        return Optional.ofNullable(this.files.get(type));
    }

    @Override
    public String save(
            final String folder,
            final ElementDefinition definition,
            final Optional<Origin> existing,
            final ElementKeys added) {
        this.saved.add(definition);
        this.savedKeys.add(added);
        return existing.map(Origin::file).orElse(definition.type().fileName() + ".yaml");
    }

    @Override
    public String createRealm(
            final String folder, final TemplatedValue realm, final DefinitionFormat format, final ElementKeys added) {
        this.created.add(folder);
        this.createdKeys.add(added);
        return "00-realm" + format.extension();
    }

    @Override
    public ElementKeys preview(
            final String folder,
            final ElementDefinition definition,
            final Optional<Origin> existing,
            final ElementKeys added) {
        return this.previewed.with(added);
    }

    @Override
    public Map<Origin, ElementKeys> elementKeys(final String folder) {
        return Map.copyOf(this.elementKeys);
    }

    @Override
    public ElementKeys realmKeys(final String folder) {
        return this.realmKeys;
    }

    @Override
    public ReorganizationPlan planReorganization(final String folder) {
        return this.plan;
    }

    @Override
    public List<String> reorganize(final String folder, final ReorganizationPlan plan) {
        this.reorganized.add(plan);
        return this.written;
    }
}
