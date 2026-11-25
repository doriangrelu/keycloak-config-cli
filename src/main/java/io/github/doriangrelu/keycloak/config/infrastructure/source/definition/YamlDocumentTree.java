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
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;

/** A YAML definition file, as a SnakeYAML node tree that keeps the comments. */
final class YamlDocumentTree implements DocumentTree {

    private final MappingNode root;
    private final DumperOptions.LineBreak lineBreak;

    private YamlDocumentTree(final MappingNode root, final DumperOptions.LineBreak lineBreak) {
        this.root = root;
        this.lineBreak = lineBreak;
    }

    static YamlDocumentTree parse(final String content) {
        DumperOptions.LineBreak lineBreak =
                content.contains("\r\n") ? DumperOptions.LineBreak.WIN : DumperOptions.LineBreak.UNIX;
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
            return new YamlDocumentTree(
                    new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK), lineBreak);
        }
        if (!(node instanceof MappingNode mapping)) {
            throw new UnreadableDocumentException(node.getStartMark().getLine() + 1, "the file is not an object");
        }
        return new YamlDocumentTree(mapping, lineBreak);
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

    @Override
    public String render() {
        LoaderOptions loader = new LoaderOptions();
        loader.setProcessComments(true);
        DumperOptions dumper = new DumperOptions();
        dumper.setProcessComments(true);
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setIndent(2);
        dumper.setIndicatorIndent(2);
        dumper.setIndentWithIndicator(true);
        dumper.setWidth(120);
        dumper.setAllowUnicode(true);
        dumper.setLineBreak(this.lineBreak);
        StringWriter output = new StringWriter();
        new Yaml(loader, dumper).serialize(this.root, output);
        return output.toString();
    }

    /** An object of a YAML file. */
    static final class YamlObject implements TreeObject {

        private static final Resolver RESOLVER = new Resolver();
        private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,18}");

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
        public List<String> keys() {
            return this.node.getValue().stream()
                    .map(NodeTuple::getKeyNode)
                    .filter(ScalarNode.class::isInstance)
                    .map(key -> ((ScalarNode) key).getValue())
                    .toList();
        }

        @Override
        public Shape shape(final String key) {
            return value(key).map(YamlObject::shapeOf).orElse(Shape.NONE);
        }

        private static Shape shapeOf(final Node node) {
            return switch (node) {
                case ScalarNode scalar -> scalar.getTag() == Tag.NULL ? Shape.NONE : Shape.SCALAR;
                case MappingNode ignored -> Shape.OBJECT;
                case SequenceNode sequence ->
                    sequence.getValue().stream().anyMatch(item -> !(item instanceof ScalarNode))
                            ? Shape.OBJECTS
                            : Shape.SCALARS;
                default -> Shape.NONE;
            };
        }

        @Override
        public int line() {
            return this.node.getStartMark() == null
                    ? 1
                    : this.node.getStartMark().getLine() + 1;
        }

        @Override
        public int column() {
            return this.node.getStartMark() == null
                    ? 1
                    : this.node.getStartMark().getColumn() + 1;
        }

        @Override
        public void setText(final String key, final TemplatedValue value) {
            Optional<Node> current = value(key);
            // Whatever its type: an unchanged number or boolean stays as written
            if (current.flatMap(YamlObject::scalar).filter(value.raw()::equals).isPresent()) {
                return;
            }
            put(key, textNode(value, current), current);
        }

        @Override
        public void setScalar(final String key, final String text) {
            if (text.equals("true") || text.equals("false")) {
                setBool(key, text.equals("true"));
            } else if (INTEGER.matcher(text).matches()) {
                put(key, new ScalarNode(Tag.INT, text, null, null, DumperOptions.ScalarStyle.PLAIN), value(key));
            } else {
                setText(key, TemplatedValue.of(text));
            }
        }

        @Override
        public void setBool(final String key, final boolean value) {
            Optional<Node> current = value(key);
            if (current.flatMap(YamlObject::scalar)
                    .filter(String.valueOf(value)::equals)
                    .isPresent()) {
                return;
            }
            put(
                    key,
                    new ScalarNode(Tag.BOOL, String.valueOf(value), null, null, DumperOptions.ScalarStyle.PLAIN),
                    current);
        }

        @Override
        public void setTexts(final String key, final List<TemplatedValue> values) {
            Optional<Node> current = value(key);
            List<String> raws = values.stream().map(TemplatedValue::raw).toList();
            if (current.isPresent()
                    && current.get() instanceof SequenceNode
                    && scalars(current.get()).equals(raws)) {
                return;
            }
            DumperOptions.FlowStyle style = current.filter(SequenceNode.class::isInstance)
                    .map(node -> ((SequenceNode) node).getFlowStyle())
                    .orElse(DumperOptions.FlowStyle.BLOCK);
            List<Node> items = new ArrayList<>();
            for (TemplatedValue value : values) {
                items.add(textNode(value, Optional.empty()));
            }
            put(key, new SequenceNode(Tag.SEQ, items, style), current);
        }

        @Override
        public void remove(final String key) {
            this.node
                    .getValue()
                    .removeIf(tuple -> tuple.getKeyNode() instanceof ScalarNode scalar
                            && scalar.getValue().equals(key));
        }

        @Override
        public TreeObject objectOrCreate(final String key) {
            Optional<Node> current = value(key);
            if (current.isPresent() && current.get() instanceof MappingNode mapping) {
                return new YamlObject(mapping);
            }
            MappingNode created = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
            put(key, created, current);
            return new YamlObject(created);
        }

        @Override
        public TreeObject append(final String listKey) {
            Optional<Node> current = value(listKey);
            SequenceNode list;
            if (current.isPresent() && current.get() instanceof SequenceNode sequence) {
                list = sequence;
            } else {
                list = new SequenceNode(Tag.SEQ, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
                put(listKey, list, current);
            }
            if (list.getFlowStyle() == DumperOptions.FlowStyle.FLOW
                    && list.getValue().isEmpty()) {
                list.setFlowStyle(DumperOptions.FlowStyle.BLOCK);
            }
            MappingNode created = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
            list.getValue().add(created);
            return new YamlObject(created);
        }

        /** Replaces the value of a key, keeping its key node (and the comments above it) and its comment at the end. */
        private void put(final String key, final Node value, final Optional<Node> current) {
            current.ifPresent(old -> value.setInLineComments(old.getInLineComments()));
            List<NodeTuple> tuples = this.node.getValue();
            for (int i = 0; i < tuples.size(); i++) {
                if (tuples.get(i).getKeyNode() instanceof ScalarNode scalar
                        && scalar.getValue().equals(key)) {
                    tuples.set(i, new NodeTuple(scalar, value));
                    return;
                }
            }
            tuples.add(new NodeTuple(new ScalarNode(Tag.STR, key, null, null, DumperOptions.ScalarStyle.PLAIN), value));
        }

        /**
         * A string scalar: between double quotes when it holds a placeholder or would be read as another type, else in
         * the style of the value it replaces.
         */
        private static ScalarNode textNode(final TemplatedValue value, final Optional<Node> current) {
            DumperOptions.ScalarStyle style;
            if (value.isTemplated() || RESOLVER.resolve(NodeId.scalar, value.raw(), true) != Tag.STR) {
                style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
            } else {
                style = current.filter(ScalarNode.class::isInstance)
                        .map(node -> ((ScalarNode) node).getScalarStyle())
                        .orElse(DumperOptions.ScalarStyle.PLAIN);
            }
            return new ScalarNode(Tag.STR, value.raw(), null, null, style);
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
