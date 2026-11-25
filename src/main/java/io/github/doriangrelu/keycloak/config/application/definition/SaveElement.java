package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.DeclarativeRule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmContext;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleEngine;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Verdict;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes an element of a realm, once nothing stands against it (ADR-008): the realm is not read-only, the file to
 * change is in the layout of the shell, and the first element of a type is written only once confirmed.
 */
public final class SaveElement {

    private final RealmDefinitions definitions;
    private final RuleBook rules;

    public SaveElement(final RealmDefinitions definitions, final RuleBook rules) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * @param existing where the element is written, when it exists
     * @param declareType whether writing the first element of its type is confirmed
     * @return the file written, and what the rules say about the change
     * @throws ReadOnlyRealmException when a file of the realm cannot be read before rendering
     * @throws UnformattedFileException when the file to change is not formatted yet
     * @throws FirstDeclarationException when the element is the first of its type and this is not confirmed
     * @throws RuleViolationException when the change introduces the failure of a required rule: nothing is written
     */
    public SaveResult save(
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
        if (!realm.declares(definition) && !declareType) {
            throw new FirstDeclarationException(definition);
        }
        RuleElement element = RuleElement.of(definition.type());
        RuleEngine engine = this.rules.engine();
        Optional<ElementKeys> before = existing.flatMap(origin ->
                Optional.ofNullable(this.definitions.elementKeys(folder).get(origin)));
        ElementKeys raw = this.definitions.preview(folder, definition, existing, ElementKeys.empty());
        ElementKeys added =
                proposesDefaults(element, existing, before, raw) ? engine.defaults(element, raw) : ElementKeys.empty();
        ElementKeys after =
                added.values().isEmpty() ? raw : this.definitions.preview(folder, definition, existing, added);
        Verdict verdict =
                engine.check(element, before, after, new RealmContext(realm, existing, this.rules.builtIns()));
        if (!verdict.allowsWriting()) {
            throw new RuleViolationException(verdict.blocking());
        }
        return new SaveResult(this.definitions.save(folder, definition, existing, added), verdict, added);
    }

    /** The defaults come with the creation of an element, and when a client changes its application type. */
    private static boolean proposesDefaults(
            final RuleElement element,
            final Optional<Origin> existing,
            final Optional<ElementKeys> before,
            final ElementKeys raw) {
        if (existing.isEmpty()) {
            return true;
        }
        return element == RuleElement.CLIENT
                && before.isPresent()
                && !DeclarativeRule.applicationTypeOf(before.get()).equals(DeclarativeRule.applicationTypeOf(raw));
    }
}
