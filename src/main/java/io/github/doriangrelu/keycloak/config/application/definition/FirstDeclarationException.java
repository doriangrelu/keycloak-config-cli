package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;

/**
 * The element would be the first of its type in the realm: the import would then delete every element of that type
 * that Keycloak holds and the files do not declare (ADR-007, ADR-008). Written only once confirmed.
 */
public final class FirstDeclarationException extends ApplicationException {

    public FirstDeclarationException(final ElementDefinition element) {
        super(warning(element) + " In a script, add --declare-type.");
    }

    /** What the user is asked to confirm: the import will delete what the files do not declare. */
    public static String warning(final ElementDefinition element) {
        return "At the next import, all the " + cleanedWith(element)
                + " of Keycloak missing from the files will be deleted. Run a dry run first, then confirm.";
    }

    /** What the import cleans with this element: its type, or the realm roles, or the roles of its client. */
    private static String cleanedWith(final ElementDefinition element) {
        if (element instanceof RoleDefinition role) {
            return role.clientId()
                    .map(clientId -> "roles of client '" + clientId.raw() + "'")
                    .orElse("realm roles");
        }
        return element.type().key();
    }
}
