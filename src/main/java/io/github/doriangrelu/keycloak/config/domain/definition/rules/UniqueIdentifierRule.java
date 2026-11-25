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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * An element of the realm must be the only one with its identifier: the {@code clientId} of a client, the name of a
 * role among the roles of its client (or the realm roles), the path of a group. One instance per kind of element.
 */
public final class UniqueIdentifierRule implements Rule {

    private static final KeyPath CLIENT = KeyPath.parse("client");

    private final RuleElement element;

    public UniqueIdentifierRule(final RuleElement element) {
        if (element == RuleElement.REALM) {
            throw new IllegalArgumentException("The realm has no identifier to keep unique");
        }
        this.element = element;
    }

    @Override
    public String id() {
        return switch (this.element) {
            case CLIENT -> "client.unique-identifier";
            case ROLE -> "role.unique-identifier";
            default -> "group.unique-identifier";
        };
    }

    @Override
    public RuleElement element() {
        return this.element;
    }

    @Override
    public Severity severity() {
        return Severity.REQUIRED;
    }

    @Override
    public String message() {
        return switch (this.element) {
            case CLIENT -> "Another client of the realm already has this clientId.";
            case ROLE -> "Another role of the realm (or of the same client) already has this name.";
            default -> "Another group of the realm already has this path.";
        };
    }

    @Override
    public boolean appliesTo(final ElementKeys keys) {
        return true;
    }

    @Override
    public Outcome check(final ElementKeys keys, final RealmContext context) {
        Optional<KeyValue> identifier = keys.get(this.element.identifierKey());
        if (identifier.isEmpty()) {
            return Outcome.PASSED;
        }
        if (!(identifier.get() instanceof KeyValue.Scalar scalar) || scalar.isTemplated()) {
            return Outcome.UNVERIFIABLE;
        }
        Optional<String> client = Optional.empty();
        if (this.element == RuleElement.ROLE) {
            Optional<KeyValue> clientKey = keys.get(CLIENT);
            if (clientKey.isPresent()) {
                if (!(clientKey.get() instanceof KeyValue.Scalar clientScalar) || clientScalar.isTemplated()) {
                    return Outcome.UNVERIFIABLE;
                }
                client = Optional.of(clientScalar.value().raw());
            }
        }
        RealmDefinition realm = context.realm();
        String name = scalar.value().raw();
        boolean duplicated = switch (this.element) {
            case CLIENT -> clash(realm.clients(), context, c -> c.clientId().raw(), name);
            case GROUP -> clash(realm.groups(), context, GroupDefinition::path, name);
            default -> clashRole(realm.roles(), context, name, client);
        };
        return duplicated ? Outcome.FAILED : Outcome.PASSED;
    }

    private static <T extends ElementDefinition> boolean clash(
            final List<Defined<T>> others,
            final RealmContext context,
            final Function<T, String> identifier,
            final String name) {
        return others.stream()
                .filter(other -> !isSelf(other, context))
                .anyMatch(other -> identifier.apply(other.definition()).equals(name));
    }

    private static boolean clashRole(
            final List<Defined<RoleDefinition>> roles,
            final RealmContext context,
            final String name,
            final Optional<String> client) {
        return roles.stream()
                .filter(other -> !isSelf(other, context))
                .map(Defined::definition)
                .anyMatch(other -> other.name().raw().equals(name)
                        && other.clientId().map(TemplatedValue::raw).equals(client));
    }

    private static boolean isSelf(final Defined<?> other, final RealmContext context) {
        return context.self().filter(other.origin()::equals).isPresent();
    }
}
