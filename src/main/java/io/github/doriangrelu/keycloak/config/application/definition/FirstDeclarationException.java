package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;

/**
 * The element would be the first of its type in the realm: the import would then delete every element of that type
 * that Keycloak holds and the files do not declare (ADR-007, ADR-008). Written only once confirmed.
 */
public final class FirstDeclarationException extends ApplicationException {

    public FirstDeclarationException(final ElementType type) {
        super("At the next import, all the " + type.key()
                + " of Keycloak missing from the files will be deleted. Run a dry run first, then confirm.");
    }
}
