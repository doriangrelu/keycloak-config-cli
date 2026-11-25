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

import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakBuiltIns;
import io.github.doriangrelu.keycloak.config.application.definition.port.RuleFiles;
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Check;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.DeclarativeRule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Disablement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Operator;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Severity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-built rules and views for the use case tests. */
final class Rules {

    static final DeclarativeRule SPA_PKCE = new DeclarativeRule(
            "client.spa.pkce",
            RuleElement.CLIENT,
            Optional.of(ApplicationType.SPA),
            List.of(),
            keys("pkce", "S256"),
            List.of(new Check(KeyPath.parse("pkce"), Operator.EQUALS, List.of("S256"))),
            Severity.REQUIRED,
            "A SPA uses PKCE.");

    static final DeclarativeRule NO_WILDCARD = new DeclarativeRule(
            "client.no-wildcard",
            RuleElement.CLIENT,
            Optional.empty(),
            List.of(),
            ElementKeys.empty(),
            List.of(new Check(KeyPath.parse("redirectUris"), Operator.NONE_MATCHES, List.of(".*[*].*"))),
            Severity.REQUIRED,
            "A redirect URI has no wildcard.");

    static final DeclarativeRule DESCRIBED = new DeclarativeRule(
            "client.described",
            RuleElement.CLIENT,
            Optional.empty(),
            List.of(),
            ElementKeys.empty(),
            List.of(new Check(KeyPath.parse("description"), Operator.PRESENT, List.of())),
            Severity.ADVICE,
            "Describe the client.");

    private Rules() {}

    static RuleBook none() {
        return book(OrganisationRules.none());
    }

    static RuleBook of(final Rule... rules) {
        return book(OrganisationRules.none(), rules);
    }

    static RuleBook disabling(final String id, final Rule... rules) {
        return book(new OrganisationRules(List.of(), List.of(new Disablement(id, java.util.Set.of(), "why"))), rules);
    }

    private static RuleBook book(final OrganisationRules organisation, final Rule... rules) {
        RuleFiles files = new RuleFiles() {
            @Override
            public List<Rule> base() {
                return List.of(rules);
            }

            @Override
            public OrganisationRules organisation() {
                return organisation;
            }
        };
        KeycloakBuiltIns builtIns = BuiltIns::none;
        return new RuleBook(files, List.of(), builtIns);
    }

    static ElementKeys keys(final String... pairs) {
        Map<KeyPath, KeyValue> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(KeyPath.parse(pairs[i]), new KeyValue.Scalar(TemplatedValue.of(pairs[i + 1])));
        }
        return new ElementKeys(values);
    }

    /** The view of a client of this type, with the flows as the adapter writes them. */
    static ElementKeys client(final ApplicationType type, final String... more) {
        String[] flows = switch (type) {
            case SPA -> new String[] {"true", "true", "false", "false", "false"};
            case API -> new String[] {"false", "false", "false", "false", "false"};
            default -> throw new IllegalArgumentException(type.name());
        };
        String[] pairs = new String[10 + more.length];
        String[] names = {
            "publicClient",
            "standardFlowEnabled",
            "implicitFlowEnabled",
            "directAccessGrantsEnabled",
            "serviceAccountsEnabled"
        };
        for (int i = 0; i < 5; i++) {
            pairs[2 * i] = names[i];
            pairs[2 * i + 1] = flows[i];
        }
        System.arraycopy(more, 0, pairs, 10, more.length);
        return keys(pairs);
    }

    static ElementKeys withList(final ElementKeys base, final String key, final String... items) {
        Map<KeyPath, KeyValue> values = new LinkedHashMap<>(base.values());
        values.put(
                KeyPath.parse(key),
                new KeyValue.Listed(
                        java.util.Arrays.stream(items).map(TemplatedValue::of).toList()));
        return new ElementKeys(values);
    }
}
