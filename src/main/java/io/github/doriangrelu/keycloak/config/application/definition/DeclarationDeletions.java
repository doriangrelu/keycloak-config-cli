/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2026 keycloak-config-cli contributors
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.application.importation.ImportReport;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tells what the next import would delete on a target once an element is the first of its type in a realm: the files
 * with the element are simulated on the target (a dry run, nothing is written) and the deletions of its type are read
 * in the plan. Only the values of the variables already known are used; nothing is asked and nothing is remembered.
 */
public final class DeclarationDeletions {

    private static final int LISTED = 10;

    private final ResolveVariables variables;
    private final RealmDefinitions definitions;

    public DeclarationDeletions(final ResolveVariables variables, final RealmDefinitions definitions) {
        this.variables = Objects.requireNonNull(variables, "variables");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * The warning of the first declarations on this connection: the question lists the deletions, and so does the
     * information given when the declaration is confirmed in advance.
     */
    public DeclarationWarning on(final Connection connection, final VariableValues remembered) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(remembered, "remembered");
        return new DeclarationWarning() {
            @Override
            public String of(final String folder, final ElementDefinition element) {
                return warning(folder, element, connection, remembered);
            }

            @Override
            public Optional<String> information(final String folder, final ElementDefinition element) {
                return Optional.of(DeclarationDeletions.this.information(folder, element, connection, remembered));
            }
        };
    }

    /** The text to confirm: the deletions the simulation shows, or the general warning when it cannot be made. */
    public String warning(
            final String folder,
            final ElementDefinition element,
            final Connection connection,
            final VariableValues remembered) {
        return text(folder, element, connection, remembered, " Confirm?");
    }

    /** The same text without the question, for a command that confirms in advance. */
    public String information(
            final String folder,
            final ElementDefinition element,
            final Connection connection,
            final VariableValues remembered) {
        return text(folder, element, connection, remembered, "");
    }

    private String text(
            final String folder,
            final ElementDefinition element,
            final Connection connection,
            final VariableValues remembered,
            final String ending) {
        String target = connection.target().name();
        String reason;
        try {
            Map<String, String> values = this.variables.resolveKnown(folder, remembered);
            ImportReport report = connection.dryRun(this.definitions.importFilesWith(folder, element, values));
            return deletions(element, values, report, target, ending);
        } catch (MissingVariableException e) {
            reason = "variable '" + e.variable() + "' has no value";
        } catch (KeycloakUnreachableException e) {
            reason = "Keycloak unreachable";
        } catch (KeycloakAuthenticationException e) {
            reason = "Keycloak refused the dry run";
        } catch (InvalidImportException e) {
            reason = "a file is invalid";
        } catch (RuntimeException e) {
            // advisory: whatever fails, the message of the failure may quote a value and is never shown
            reason = "the dry run failed";
        }
        return FirstDeclarationException.warning(element) + " (dry run on " + target + " not possible: " + reason + ")";
    }

    private static String deletions(
            final ElementDefinition element,
            final Map<String, String> values,
            final ImportReport report,
            final String target,
            final String ending) {
        String cleaned = FirstDeclarationException.cleanedWith(element);
        List<String> names = new ArrayList<>();
        for (List<PlannedChange> plan : report.plans().values()) {
            for (PlannedChange change : plan) {
                if (change.kind() == PlannedChange.Kind.DELETE && concerns(element, values, change)) {
                    names.add(nameOf(change.key()));
                }
            }
        }
        if (names.isEmpty()) {
            return "No " + cleaned + " of Keycloak would be deleted on " + target + " now, but at each import the "
                    + cleaned + " missing from the files will be." + ending;
        }
        String listed = names.stream().limit(LISTED).collect(Collectors.joining(", "));
        String more = names.size() > LISTED ? " and " + (names.size() - LISTED) + " more" : "";
        return "At the next import, these " + cleaned + " of Keycloak missing from the files will be deleted on "
                + target + " (" + names.size() + "): " + listed + more + "." + ending;
    }

    private static boolean concerns(
            final ElementDefinition element, final Map<String, String> values, final PlannedChange change) {
        if (element instanceof RoleDefinition role) {
            if (role.clientId().isEmpty()) {
                return change.type() == ResourceType.REALM_ROLE;
            }
            // the plan holds the rendered client id
            return change.type() == ResourceType.CLIENT_ROLE
                    && change.key() instanceof RoleKey key
                    && key.client()
                            .filter(role.clientId().get().rendered(values)::equals)
                            .isPresent();
        }
        return change.type() == (element instanceof GroupDefinition ? ResourceType.GROUP : ResourceType.CLIENT);
    }

    private static String nameOf(final Object key) {
        return key instanceof RoleKey role ? role.name() : String.valueOf(key);
    }
}
