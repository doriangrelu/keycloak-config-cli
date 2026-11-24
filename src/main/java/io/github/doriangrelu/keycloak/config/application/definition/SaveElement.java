package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes an element of a realm, once nothing stands against it (ADR-008): the realm is not read-only, the file to
 * change is in the layout of the shell, and the first element of a type is written only once confirmed.
 */
public final class SaveElement {

    private final RealmDefinitions definitions;

    public SaveElement(final RealmDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * @param existing where the element is written, when it exists
     * @param declareType whether writing the first element of its type is confirmed
     * @return the file written
     * @throws ReadOnlyRealmException when a file of the realm cannot be read before rendering
     * @throws UnformattedFileException when the file to change is not formatted yet
     * @throws FirstDeclarationException when the element is the first of its type and this is not confirmed
     */
    public String save(
            final String folder,
            final ElementDefinition definition,
            final Optional<Origin> existing,
            final boolean declareType) {
        RealmDefinition realm =
                this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
        if (realm.isReadOnly()) {
            throw new ReadOnlyRealmException(realm.unreadableFiles().getFirst());
        }
        Optional<String> target =
                existing.map(Origin::file).or(() -> this.definitions.fileOf(folder, definition.type()));
        if (target.isPresent() && this.definitions.unformattedFiles(folder).contains(target.get())) {
            throw new UnformattedFileException(target.get());
        }
        if (!realm.declaredTypes().contains(definition.type()) && !declareType) {
            throw new FirstDeclarationException(definition.type());
        }
        return this.definitions.save(folder, definition, existing);
    }
}
