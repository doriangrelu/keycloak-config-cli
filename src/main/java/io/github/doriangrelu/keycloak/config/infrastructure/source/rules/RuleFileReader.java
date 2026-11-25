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

package io.github.doriangrelu.keycloak.config.infrastructure.source.rules;

import io.github.doriangrelu.keycloak.config.domain.common.DomainException;
import io.github.doriangrelu.keycloak.config.domain.definition.ApplicationType;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Check;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.DeclarativeRule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Disablement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.InvalidRulesException;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Operator;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.OrganisationRules;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Rule;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Severity;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Reads a rules file (ADR-009). Every problem is an {@link InvalidRulesException} whose message starts with the name of
 * the file and, when it is known, the rule.
 */
public final class RuleFileReader {

    private static final Set<String> RULES_ONLY = Set.of("rules");
    private static final Set<String> ORGANISATION_FIELDS = Set.of("rules", "disable");
    private static final Set<String> RULE_FIELDS =
            Set.of("id", "element", "when", "defaults", "checks", "severity", "message");
    private static final Set<String> WHEN_FIELDS = Set.of("application-type", "checks");
    private static final Set<String> DISABLE_FIELDS = Set.of("id", "for", "reason");
    private static final Set<String> LIST_KEYS = Set.of("redirectUris", "webOrigins", "realmRoles", "defaultRoles");

    private RuleFileReader() {}

    /** The rules of a file; its {@code disable} section is not accepted. */
    public static List<Rule> rules(final String fileName, final String yaml) {
        return read(fileName, yaml, RULES_ONLY).rules();
    }

    /** The rules and the disablements of an organisation file. */
    public static OrganisationRules organisation(final String fileName, final String yaml) {
        return read(fileName, yaml, ORGANISATION_FIELDS);
    }

    private static OrganisationRules read(final String fileName, final String yaml, final Set<String> allowed) {
        try {
            Map<String, Object> root = parse(yaml);
            unknownFields(root, allowed, "the file");
            return new OrganisationRules(
                    rules(list(root.get("rules"), "rules")), disablements(list(root.get("disable"), "disable")));
        } catch (InvalidRulesException e) {
            throw new InvalidRulesException(fileName + ": " + e.getMessage());
        }
    }

    private static Map<String, Object> parse(final String yaml) {
        Node root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(options).compose(new StringReader(yaml));
        } catch (YAMLException e) {
            throw new InvalidRulesException("the file is not valid YAML: " + e.getMessage());
        }
        if (root == null) {
            return Map.of();
        }
        return map(plain(root), "the file");
    }

    /** The node as maps, lists and texts: a scalar keeps the text it is written with, an empty one is null. */
    private static Object plain(final Node node) {
        if (node instanceof ScalarNode scalar) {
            return Tag.NULL.equals(scalar.getTag()) ? null : scalar.getValue();
        }
        if (node instanceof SequenceNode sequence) {
            List<Object> items = new ArrayList<>();
            sequence.getValue().forEach(item -> items.add(plain(item)));
            return items;
        }
        Map<Object, Object> map = new LinkedHashMap<>();
        for (NodeTuple tuple : ((MappingNode) node).getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                throw new InvalidRulesException("a key must be a text");
            }
            if (map.containsKey(key.getValue())) {
                throw new InvalidRulesException("duplicate key '" + key.getValue() + "'");
            }
            map.put(key.getValue(), plain(tuple.getValueNode()));
        }
        return map;
    }

    private static List<Rule> rules(final List<Object> entries) {
        List<Rule> rules = new ArrayList<>();
        Map<DefaultsScope, Map<KeyPath, DefaultOrigin>> defaultsByScope = new HashMap<>();
        for (Object entry : entries) {
            DeclarativeRule rule = rule(entry);
            rules.add(rule);
            rejectConflictingDefaults(rule, defaultsByScope);
        }
        return rules;
    }

    private static DeclarativeRule rule(final Object entry) {
        String id = "";
        try {
            Map<String, Object> fields = map(entry, "a rule");
            id = text(fields.get("id"), "id").orElse("");
            unknownFields(fields, RULE_FIELDS, "a rule");
            RuleElement element = element(fields.get("element"));
            Optional<ApplicationType> applicationType = Optional.empty();
            List<Check> conditions = List.of();
            if (fields.get("when") != null) {
                Map<String, Object> when = map(fields.get("when"), "'when'");
                unknownFields(when, WHEN_FIELDS, "'when'");
                applicationType =
                        text(when.get("application-type"), "application-type").map(RuleFileReader::applicationType);
                conditions = checks(list(when.get("checks"), "when.checks"));
            }
            return new DeclarativeRule(
                    id,
                    element,
                    applicationType,
                    conditions,
                    defaults(fields.get("defaults")),
                    checks(list(fields.get("checks"), "checks")),
                    severity(fields.get("severity")),
                    text(fields.get("message"), "message").orElse(null));
        } catch (DomainException e) {
            String where = id.isBlank() ? "" : "rule '" + id + "': ";
            throw new InvalidRulesException(where + e.getMessage());
        }
    }

    private static RuleElement element(final Object value) {
        String name = text(value, "element").orElseThrow(() -> new InvalidRulesException("'element' is missing"));
        try {
            return RuleElement.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRulesException("'element' must be realm, client, role or group, not '" + name + "'");
        }
    }

    private static ApplicationType applicationType(final String name) {
        try {
            return ApplicationType.valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new InvalidRulesException(
                    "'application-type' must be spa, web-application, service, api or custom, not '" + name + "'");
        }
    }

    private static Severity severity(final Object value) {
        Optional<String> name = text(value, "severity");
        if (name.isEmpty()) {
            return Severity.REQUIRED;
        }
        return switch (name.get()) {
            case "required" -> Severity.REQUIRED;
            case "advice" -> Severity.ADVICE;
            default ->
                throw new InvalidRulesException("'severity' must be required or advice, not '" + name.get() + "'");
        };
    }

    private static ElementKeys defaults(final Object value) {
        if (value == null) {
            return ElementKeys.empty();
        }
        Map<KeyPath, KeyValue> defaults = new LinkedHashMap<>();
        map(value, "'defaults'")
                .forEach((key, operand) -> defaults.put(KeyPath.parse(key), defaultValue(key, operand)));
        return new ElementKeys(defaults);
    }

    private static KeyValue defaultValue(final String key, final Object value) {
        String what = "the default of '" + key + "'";
        if (value instanceof List<?> items) {
            List<TemplatedValue> values = new ArrayList<>();
            for (Object item : items) {
                values.add(TemplatedValue.of(scalar(item, what)));
            }
            return new KeyValue.Listed(values);
        }
        return new KeyValue.Scalar(TemplatedValue.of(scalar(value, what)));
    }

    private static List<Check> checks(final List<Object> entries) {
        List<Check> checks = new ArrayList<>();
        for (Object entry : entries) {
            checks.add(check(entry));
        }
        return checks;
    }

    private static Check check(final Object entry) {
        Map<String, Object> fields = map(entry, "a check");
        String key =
                text(fields.get("key"), "key").orElseThrow(() -> new InvalidRulesException("a check needs a 'key'"));
        List<String> operators = new ArrayList<>();
        for (String field : fields.keySet()) {
            if (!field.equals("key")) {
                if (Operator.named(field).isEmpty()) {
                    throw new InvalidRulesException(
                            "unknown field or operator '" + field + "' in the check on '" + key + "'");
                }
                operators.add(field);
            }
        }
        if (operators.size() != 1) {
            throw new InvalidRulesException(
                    "the check on '" + key + "' needs exactly one operator, found " + operators);
        }
        Operator operator = Operator.named(operators.get(0)).orElseThrow();
        KeyPath path = KeyPath.parse(key);
        return new Check(path, operator, operands(path, operator, fields.get(operators.get(0))));
    }

    private static List<String> operands(final KeyPath key, final Operator operator, final Object operand) {
        String name = operator.name().toLowerCase(Locale.ROOT).replace('_', '-');
        String what = "'" + name + "' on '" + key + "'";
        return switch (operator) {
            case PRESENT, ABSENT -> {
                if (operand != null && !"true".equals(operand)) {
                    throw new InvalidRulesException(what + " takes no operand (or true)");
                }
                yield List.of();
            }
            case ONE_OF, NOT_ONE_OF -> {
                if (!(operand instanceof List<?> items)) {
                    throw new InvalidRulesException(what + " needs a list of values");
                }
                List<String> values = new ArrayList<>();
                for (Object item : items) {
                    values.add(scalar(item, what));
                }
                yield values;
            }
            case EQUALS -> {
                if (operand instanceof List) {
                    throw new InvalidRulesException(what + " does not accept a list, use one-of or not-one-of");
                }
                if (LIST_KEYS.contains(key.toString())) {
                    throw new InvalidRulesException(
                            what + " cannot be used on a list key, use one-of, matches or none-matches");
                }
                yield List.of(scalar(operand, what));
            }
            case MATCHES, NONE_MATCHES -> {
                if (operand instanceof List) {
                    throw new InvalidRulesException(what + " takes one pattern, not a list");
                }
                yield List.of(scalar(operand, what));
            }
        };
    }

    private static void rejectConflictingDefaults(
            final DeclarativeRule rule, final Map<DefaultsScope, Map<KeyPath, DefaultOrigin>> defaultsByScope) {
        DefaultsScope scope = new DefaultsScope(rule.element(), rule.applicationType(), rule.conditions());
        Map<KeyPath, DefaultOrigin> known = defaultsByScope.computeIfAbsent(scope, s -> new HashMap<>());
        rule.defaults().values().forEach((key, value) -> {
            DefaultOrigin previous = known.putIfAbsent(key, new DefaultOrigin(rule.id(), value));
            if (previous != null && !previous.value().equals(value)) {
                throw new InvalidRulesException("rule '" + rule.id() + "': the key '" + key
                        + "' already has another default value in the rule '" + previous.ruleId()
                        + "' for the same element and conditions");
            }
        });
    }

    private static List<Disablement> disablements(final List<Object> entries) {
        List<Disablement> disablements = new ArrayList<>();
        for (Object entry : entries) {
            String id = "";
            try {
                Map<String, Object> fields = map(entry, "a disablement");
                id = text(fields.get("id"), "id").orElse("");
                unknownFields(fields, DISABLE_FIELDS, "a disablement");
                Set<String> forElements = new LinkedHashSet<>();
                for (Object element : list(fields.get("for"), "for")) {
                    forElements.add(scalar(element, "'for'"));
                }
                disablements.add(new Disablement(
                        id, forElements, text(fields.get("reason"), "reason").orElse(null)));
            } catch (DomainException e) {
                throw new InvalidRulesException((id.isBlank() ? "" : "disable '" + id + "': ") + e.getMessage());
            }
        }
        return disablements;
    }

    private static void unknownFields(final Map<String, Object> fields, final Set<String> known, final String where) {
        for (String field : fields.keySet()) {
            if (!known.contains(field)) {
                throw new InvalidRulesException("unknown field '" + field + "' in " + where + " (expected: "
                        + String.join(", ", known.stream().sorted().toList()) + ")");
            }
        }
    }

    private static Map<String, Object> map(final Object value, final String what) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new InvalidRulesException(what + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<Object> list(final Object value, final String what) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items)) {
            throw new InvalidRulesException("'" + what + "' must be a list");
        }
        return new ArrayList<>(items);
    }

    private static Optional<String> text(final Object value, final String what) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(scalar(value, "'" + what + "'"));
    }

    /** A YAML text, boolean or number, as its text. */
    private static String scalar(final Object value, final String what) {
        if (value == null || value instanceof Map || value instanceof List) {
            throw new InvalidRulesException(what + " must be a text, a number or a boolean");
        }
        return String.valueOf(value);
    }

    /** What decides whether two rules of a file may give another default to the same key. */
    private record DefaultsScope(
            RuleElement element, Optional<ApplicationType> applicationType, List<Check> conditions) {}

    private record DefaultOrigin(String ruleId, KeyValue value) {}
}
