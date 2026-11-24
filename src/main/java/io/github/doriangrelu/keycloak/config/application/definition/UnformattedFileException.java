package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** The file to change is not in the layout of the shell yet: writing it would also change its layout (ADR-008). */
public final class UnformattedFileException extends ApplicationException {

    public UnformattedFileException(final String file) {
        super("Run 'realm format' first: " + file);
    }
}
