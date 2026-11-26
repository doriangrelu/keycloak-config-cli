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

package io.github.doriangrelu.keycloak.config.infrastructure.source.targets;

import io.github.doriangrelu.keycloak.config.application.definition.port.Targets;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.convert.DurationStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * The targets declared in {@code <root>/kcc-targets.yaml} (ADR-010). The file is read on every call, so an edit is
 * seen without restarting the shell. A missing file means no target; a problem is an
 * {@link InvalidTargetsFileException} whose message starts with the file and the target.
 */
public final class TargetsFile implements Targets {

    static final String FILE_NAME = "kcc-targets.yaml";

    private static final Set<String> FIELDS =
            Set.of("url", "login-realm", "client-id", "grant-type", "user", "ssl-verify", "connect-timeout");
    private static final Set<String> SECRET_FIELDS = Set.of("password", "client-secret", "secret");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path file;

    public TargetsFile(final Path root) {
        this.file = root.resolve(FILE_NAME);
    }

    @Override
    public List<Target> all() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        String yaml;
        try {
            yaml = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidTargetsFileException(FILE_NAME + ": cannot be read: " + e.getMessage());
        }
        return read(yaml);
    }

    @Override
    public Optional<Target> find(final String name) {
        return all().stream().filter(target -> target.name().equals(name)).findFirst();
    }

    private static List<Target> read(final String yaml) {
        Node root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(options).compose(new StringReader(yaml));
        } catch (YAMLException e) {
            throw new InvalidTargetsFileException(FILE_NAME + ": the file is not valid YAML: " + e.getMessage());
        }
        if (root == null) {
            return List.of();
        }
        Map<String, Node> top = mapping(root, FILE_NAME + ": the file");
        for (String key : top.keySet()) {
            if (!key.equals("targets")) {
                throw new InvalidTargetsFileException(FILE_NAME + ": unknown field '" + key + "' (expected: targets)");
            }
        }
        Node targets = top.get("targets");
        if (targets == null || isNull(targets)) {
            return List.of();
        }
        List<Target> result = new ArrayList<>();
        mapping(targets, FILE_NAME + ": 'targets'").forEach((name, node) -> result.add(target(name, node)));
        return result;
    }

    private static Target target(final String name, final Node node) {
        String where = FILE_NAME + ": target '" + name + "': ";
        if (!NAME.matcher(name).matches()) {
            throw new InvalidTargetsFileException(where + "a name holds letters, digits, '-' and '_'");
        }
        Map<String, Node> fields = mapping(node, where + "the target");
        for (String field : fields.keySet()) {
            if (SECRET_FIELDS.contains(field)) {
                throw new InvalidTargetsFileException(where + "'" + field + "': secrets do not belong in "
                        + FILE_NAME + " (the password or secret is asked for when connecting, or read from "
                        + "the environment)");
            }
            if (!FIELDS.contains(field)) {
                throw new InvalidTargetsFileException(where + "unknown field '" + field + "' (expected: "
                        + String.join(", ", FIELDS.stream().sorted().toList()) + ")");
            }
        }
        String url = text(fields, "url", where)
                .orElseThrow(() -> new InvalidTargetsFileException(where + "'url' is missing"));
        GrantType grantType = text(fields, "grant-type", where)
                .map(value -> GrantType.of(value)
                        .orElseThrow(() -> new InvalidTargetsFileException(
                                where + "'grant-type' must be password or client_credentials, not '" + value + "'")))
                .orElse(GrantType.PASSWORD);
        Optional<String> user = text(fields, "user", where);
        if (grantType == GrantType.PASSWORD && user.isEmpty()) {
            throw new InvalidTargetsFileException(where + "'user' is missing (needed by the password grant)");
        }
        return new Target(
                name,
                url,
                text(fields, "login-realm", where).orElse("master"),
                text(fields, "client-id", where).orElse("admin-cli"),
                grantType,
                user,
                sslVerify(fields, where),
                connectTimeout(fields, where));
    }

    private static boolean sslVerify(final Map<String, Node> fields, final String where) {
        return text(fields, "ssl-verify", where)
                .map(value -> switch (value) {
                    case "true" -> true;
                    case "false" -> false;
                    default ->
                        throw new InvalidTargetsFileException(
                                where + "'ssl-verify' must be true or false, not '" + value + "'");
                })
                .orElse(true);
    }

    private static Duration connectTimeout(final Map<String, Node> fields, final String where) {
        return text(fields, "connect-timeout", where)
                .map(value -> {
                    Duration duration;
                    try {
                        duration = DurationStyle.detectAndParse(value);
                    } catch (IllegalArgumentException e) {
                        throw new InvalidTargetsFileException(where + "'connect-timeout' must be a duration "
                                + "such as 10s, 500ms or 1m, not '" + value + "'");
                    }
                    if (duration.isZero() || duration.isNegative()) {
                        throw new InvalidTargetsFileException(
                                where + "'connect-timeout' must be longer than zero, not '" + value + "'");
                    }
                    return duration;
                })
                .orElse(Duration.ofSeconds(10));
    }

    private static Optional<String> text(final Map<String, Node> fields, final String field, final String where) {
        Node node = fields.get(field);
        if (node == null || isNull(node)) {
            return Optional.empty();
        }
        if (!(node instanceof ScalarNode scalar)) {
            throw new InvalidTargetsFileException(where + "'" + field + "' must be a text, a number or a boolean");
        }
        String value = scalar.getValue().strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static boolean isNull(final Node node) {
        return node instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag());
    }

    private static Map<String, Node> mapping(final Node node, final String what) {
        if (!(node instanceof MappingNode mapping)) {
            throw new InvalidTargetsFileException(what + " must be an object");
        }
        Map<String, Node> result = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                throw new InvalidTargetsFileException(what + ": a key must be a text");
            }
            if (result.putIfAbsent(key.getValue(), tuple.getValueNode()) != null) {
                throw new InvalidTargetsFileException(what + ": duplicate key '" + key.getValue() + "'");
            }
        }
        return result;
    }
}
