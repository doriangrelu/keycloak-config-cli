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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/** A JSON definition file, as a Jackson tree that remembers the line of each object. */
final class JsonDocumentTree implements DocumentTree {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ObjectNode root;
    private final Map<JsonNode, int[]> lines;
    private final String lineSeparator;

    private JsonDocumentTree(final ObjectNode root, final Map<JsonNode, int[]> lines, final String lineSeparator) {
        this.root = root;
        this.lines = lines;
        this.lineSeparator = lineSeparator;
    }

    static JsonDocumentTree parse(final String content) {
        Map<JsonNode, int[]> lines = new IdentityHashMap<>();
        String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        if (content.isBlank()) {
            return new JsonDocumentTree(NODES.objectNode(), lines, lineSeparator);
        }
        try (JsonParser parser = MAPPER.createParser(content)) {
            JsonToken first = parser.nextToken();
            if (first != JsonToken.START_OBJECT) {
                throw new UnreadableDocumentException(
                        parser.currentTokenLocation().getLineNr(), "the file is not an object");
            }
            ObjectNode root = (ObjectNode) read(parser, lines);
            if (parser.nextToken() != null) {
                throw new UnreadableDocumentException(
                        parser.currentTokenLocation().getLineNr(), "unexpected content after the object");
            }
            return new JsonDocumentTree(root, lines, lineSeparator);
        } catch (JsonProcessingException e) {
            int line = e.getLocation() == null ? 1 : e.getLocation().getLineNr();
            throw new UnreadableDocumentException(line, e.getOriginalMessage());
        } catch (IOException e) {
            throw new UnreadableDocumentException(1, e.getMessage());
        }
    }

    /** Reads the value at the current token, remembering the line of each object. */
    private static JsonNode read(final JsonParser parser, final Map<JsonNode, int[]> lines) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            throw new UnreadableDocumentException(1, "unexpected end of the file");
        }
        switch (token) {
            case START_OBJECT -> {
                ObjectNode object = NODES.objectNode();
                lines.put(object, new int[] {
                    parser.currentTokenLocation().getLineNr(),
                    parser.currentTokenLocation().getColumnNr()
                });
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    parser.nextToken();
                    object.set(name, read(parser, lines));
                }
                return object;
            }
            case START_ARRAY -> {
                ArrayNode array = NODES.arrayNode();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    array.add(read(parser, lines));
                }
                return array;
            }
            default -> {
                return MAPPER.readTree(parser);
            }
        }
    }

    ObjectNode node() {
        return this.root;
    }

    @Override
    public TreeObject root() {
        return new JsonObject(this.root, this.lines);
    }

    @Override
    public DefinitionFormat format() {
        return DefinitionFormat.JSON;
    }

    @Override
    public String render() {
        DefaultIndenter indenter = new DefaultIndenter("  ", this.lineSeparator);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter)
                .withSeparators(
                        Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        try {
            return MAPPER.writer(printer).writeValueAsString(this.root) + this.lineSeparator;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write a JSON tree", e);
        }
    }

    /** The value of a key of a JSON object. */
    record JsonEntry(JsonNode value) implements TreeObject.Entry {

        @Override
        public Object plain() {
            return MAPPER.convertValue(this.value, Object.class);
        }
    }

    /** An object of a JSON file. */
    static final class JsonObject implements TreeObject {

        private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,18}");

        private final ObjectNode node;
        private final Map<JsonNode, int[]> lines;

        JsonObject(final ObjectNode node, final Map<JsonNode, int[]> lines) {
            this.node = node;
            this.lines = lines;
        }

        ObjectNode node() {
            return this.node;
        }

        private Optional<JsonNode> value(final String key) {
            return Optional.ofNullable(this.node.get(key));
        }

        @Override
        public Optional<String> text(final String key) {
            return value(key).flatMap(JsonObject::scalar);
        }

        @Override
        public Optional<Boolean> bool(final String key) {
            return value(key).flatMap(value -> {
                if (value.isBoolean()) {
                    return Optional.of(value.booleanValue());
                }
                if (value.isTextual()
                        && (value.textValue().equals("true")
                                || value.textValue().equals("false"))) {
                    return Optional.of(Boolean.valueOf(value.textValue()));
                }
                return Optional.empty();
            });
        }

        @Override
        public List<String> texts(final String key) {
            return value(key).map(JsonObject::scalars).orElse(List.of());
        }

        @Override
        public Optional<TreeObject> object(final String key) {
            return value(key).filter(JsonNode::isObject).map(value -> new JsonObject((ObjectNode) value, this.lines));
        }

        @Override
        public List<TreeObject> objects(final String key) {
            return value(key).map(this::objectsOf).orElse(List.of());
        }

        @Override
        public Map<String, String> textEntries(final String key) {
            Map<String, String> entries = new LinkedHashMap<>();
            value(key)
                    .filter(JsonNode::isObject)
                    .ifPresent(value -> value.properties()
                            .forEach(entry ->
                                    scalar(entry.getValue()).ifPresent(text -> entries.put(entry.getKey(), text))));
            return entries;
        }

        @Override
        public Map<String, List<String>> textLists(final String key) {
            Map<String, List<String>> lists = new LinkedHashMap<>();
            value(key)
                    .filter(JsonNode::isObject)
                    .ifPresent(value ->
                            value.properties().forEach(entry -> lists.put(entry.getKey(), scalars(entry.getValue()))));
            return lists;
        }

        @Override
        public Map<String, List<TreeObject>> objectLists() {
            Map<String, List<TreeObject>> lists = new LinkedHashMap<>();
            this.node.properties().forEach(entry -> {
                if (entry.getValue().isArray()) {
                    lists.put(entry.getKey(), objectsOf(entry.getValue()));
                }
            });
            return lists;
        }

        @Override
        public boolean has(final String key) {
            return this.node.has(key);
        }

        @Override
        public List<String> keys() {
            return this.node.properties().stream().map(Map.Entry::getKey).toList();
        }

        @Override
        public Shape shape(final String key) {
            JsonNode value = this.node.get(key);
            if (value == null || value.isNull()) {
                return Shape.NONE;
            }
            if (value.isObject()) {
                return Shape.OBJECT;
            }
            if (value.isArray()) {
                return StreamSupport.stream(value.spliterator(), false).anyMatch(JsonNode::isContainerNode)
                        ? Shape.OBJECTS
                        : Shape.SCALARS;
            }
            return Shape.SCALAR;
        }

        @Override
        public int line() {
            int[] position = this.lines.get(this.node);
            return position == null ? 1 : position[0];
        }

        @Override
        public int column() {
            int[] position = this.lines.get(this.node);
            return position == null ? 1 : position[1];
        }

        @Override
        public void setText(final String key, final TemplatedValue value) {
            // Whatever its type: an unchanged number or boolean stays as written
            if (text(key).filter(value.raw()::equals).isPresent()) {
                return;
            }
            this.node.put(key, value.raw());
        }

        @Override
        public void setScalar(final String key, final String text) {
            if (text.equals("true") || text.equals("false")) {
                setBool(key, text.equals("true"));
            } else if (INTEGER.matcher(text).matches()) {
                this.node.put(key, Long.parseLong(text));
            } else {
                setText(key, TemplatedValue.of(text));
            }
        }

        @Override
        public void setBool(final String key, final boolean value) {
            if (bool(key).filter(current -> current == value).isPresent()) {
                return;
            }
            this.node.put(key, value);
        }

        @Override
        public void setTexts(final String key, final List<TemplatedValue> values) {
            ArrayNode array = NODES.arrayNode();
            values.forEach(value -> array.add(value.raw()));
            this.node.set(key, array);
        }

        @Override
        public void remove(final String key) {
            this.node.remove(key);
        }

        @Override
        public TreeObject objectOrCreate(final String key) {
            JsonNode current = this.node.get(key);
            ObjectNode object = current instanceof ObjectNode existing ? existing : this.node.putObject(key);
            return new JsonObject(object, this.lines);
        }

        @Override
        public TreeObject append(final String listKey) {
            JsonNode current = this.node.get(listKey);
            ArrayNode array = current instanceof ArrayNode existing ? existing : this.node.putArray(listKey);
            return new JsonObject(array.addObject(), this.lines);
        }

        @Override
        public OptionalInt listSize(final String key) {
            return value(key)
                    .filter(JsonNode::isArray)
                    .map(array -> OptionalInt.of(array.size()))
                    .orElse(OptionalInt.empty());
        }

        @Override
        public Optional<Entry> entry(final String key) {
            return value(key).map(JsonEntry::new);
        }

        @Override
        public void put(final String key, final Entry entry) {
            this.node.set(key, nodeOf(entry));
        }

        @Override
        public void concat(final String key, final Entry list) {
            JsonNode current = this.node.get(key);
            JsonNode added = nodeOf(list);
            if (current instanceof ArrayNode array && added instanceof ArrayNode items) {
                array.addAll(items);
            } else {
                this.node.set(key, added);
            }
        }

        private static JsonNode nodeOf(final Entry entry) {
            return entry instanceof JsonEntry json ? json.value() : MAPPER.valueToTree(entry.plain());
        }

        private static Optional<String> scalar(final JsonNode value) {
            return value.isValueNode() && !value.isNull() ? Optional.of(value.asText()) : Optional.empty();
        }

        private static List<String> scalars(final JsonNode value) {
            if (!value.isArray()) {
                return List.of();
            }
            return StreamSupport.stream(value.spliterator(), false)
                    .map(JsonObject::scalar)
                    .flatMap(Optional::stream)
                    .toList();
        }

        private List<TreeObject> objectsOf(final JsonNode value) {
            if (!value.isArray()) {
                return List.of();
            }
            return StreamSupport.stream(value.spliterator(), false)
                    .filter(JsonNode::isObject)
                    .map(item -> (TreeObject) new JsonObject((ObjectNode) item, this.lines))
                    .toList();
        }
    }
}
