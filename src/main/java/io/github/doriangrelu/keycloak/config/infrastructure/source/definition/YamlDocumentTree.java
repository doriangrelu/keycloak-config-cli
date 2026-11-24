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

package io.github.doriangrelu.keycloak.config.infrastructure.source.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/** A YAML definition file, as a SnakeYAML node tree that keeps the comments. */
final class YamlDocumentTree implements DocumentTree {

    private final MappingNode root;

    private YamlDocumentTree(final MappingNode root) {
        this.root = root;
    }

    static YamlDocumentTree parse(final String content) {
        LoaderOptions options = new LoaderOptions();
        options.setProcessComments(true);
        Node node;
        try {
            node = new Yaml(options).compose(new StringReader(content));
        } catch (MarkedYAMLException e) {
            int line = e.getProblemMark() == null ? 1 : e.getProblemMark().getLine() + 1;
            throw new UnreadableDocumentException(line, e.getProblem());
        } catch (YAMLException e) {
            throw new UnreadableDocumentException(1, e.getMessage());
        }
        if (node == null) {
            return new YamlDocumentTree(new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK));
        }
        if (!(node instanceof MappingNode mapping)) {
            throw new UnreadableDocumentException(node.getStartMark().getLine() + 1, "the file is not an object");
        }
        return new YamlDocumentTree(mapping);
    }

    MappingNode node() {
        return this.root;
    }

    @Override
    public TreeObject root() {
        return new YamlObject(this.root);
    }

    @Override
    public DefinitionFormat format() {
        return DefinitionFormat.YAML;
    }

    /** An object of a YAML file. */
    static final class YamlObject implements TreeObject {

        private final MappingNode node;

        YamlObject(final MappingNode node) {
            this.node = node;
        }

        MappingNode node() {
            return this.node;
        }

        Optional<Node> value(final String key) {
            for (NodeTuple tuple : this.node.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode scalar
                        && scalar.getValue().equals(key)) {
                    return Optional.of(tuple.getValueNode());
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> text(final String key) {
            return value(key).flatMap(YamlObject::scalar);
        }

        @Override
        public Optional<Boolean> bool(final String key) {
            return text(key)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .filter(value -> value.equals("true") || value.equals("false"))
                    .map(Boolean::valueOf);
        }

        @Override
        public List<String> texts(final String key) {
            return value(key).map(YamlObject::scalars).orElse(List.of());
        }

        @Override
        public Optional<TreeObject> object(final String key) {
            return value(key).filter(MappingNode.class::isInstance).map(node -> new YamlObject((MappingNode) node));
        }

        @Override
        public List<TreeObject> objects(final String key) {
            return value(key).map(YamlObject::objectsOf).orElse(List.of());
        }

        @Override
        public Map<String, String> textEntries(final String key) {
            Map<String, String> entries = new LinkedHashMap<>();
            value(key).filter(MappingNode.class::isInstance).ifPresent(node -> {
                for (NodeTuple tuple : ((MappingNode) node).getValue()) {
                    scalar(tuple.getKeyNode())
                            .ifPresent(name -> scalar(tuple.getValueNode()).ifPresent(text -> entries.put(name, text)));
                }
            });
            return entries;
        }

        @Override
        public Map<String, List<String>> textLists(final String key) {
            Map<String, List<String>> lists = new LinkedHashMap<>();
            value(key).filter(MappingNode.class::isInstance).ifPresent(node -> {
                for (NodeTuple tuple : ((MappingNode) node).getValue()) {
                    scalar(tuple.getKeyNode()).ifPresent(name -> lists.put(name, scalars(tuple.getValueNode())));
                }
            });
            return lists;
        }

        @Override
        public Map<String, List<TreeObject>> objectLists() {
            Map<String, List<TreeObject>> lists = new LinkedHashMap<>();
            for (NodeTuple tuple : this.node.getValue()) {
                if (tuple.getValueNode() instanceof SequenceNode) {
                    scalar(tuple.getKeyNode()).ifPresent(name -> lists.put(name, objectsOf(tuple.getValueNode())));
                }
            }
            return lists;
        }

        @Override
        public boolean has(final String key) {
            return value(key).isPresent();
        }

        @Override
        public int line() {
            return this.node.getStartMark() == null
                    ? 1
                    : this.node.getStartMark().getLine() + 1;
        }

        private static Optional<String> scalar(final Node node) {
            return node instanceof ScalarNode scalar && scalar.getTag() != Tag.NULL
                    ? Optional.of(scalar.getValue())
                    : Optional.empty();
        }

        private static List<String> scalars(final Node node) {
            if (!(node instanceof SequenceNode sequence)) {
                return List.of();
            }
            return sequence.getValue().stream()
                    .map(YamlObject::scalar)
                    .flatMap(Optional::stream)
                    .toList();
        }

        private static List<TreeObject> objectsOf(final Node node) {
            if (!(node instanceof SequenceNode sequence)) {
                return List.of();
            }
            return sequence.getValue().stream()
                    .filter(MappingNode.class::isInstance)
                    .map(item -> (TreeObject) new YamlObject((MappingNode) item))
                    .toList();
        }
    }
}
