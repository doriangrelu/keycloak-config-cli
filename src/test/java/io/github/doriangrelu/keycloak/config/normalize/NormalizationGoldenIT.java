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

package io.github.doriangrelu.keycloak.config.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.RealmExportNormalizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Normalizes whole exports and compares the result with the expected output, so that a change of the normalization
 * shows up as a change of an expected file.
 *
 * <p>The comparison ignores the order of the object properties and of the array items. Run with
 * {@code -Dnormalization.golden.update=true} to rewrite the expected files from the current output.
 */
class NormalizationGoldenIT extends AbstractNormalizeTest {

    private static final Path RESOURCES = Path.of("src/test/resources");
    private static final Path EXPECTED = RESOURCES.resolve("normalization/expected");
    private static final boolean UPDATE = Boolean.getBoolean("normalization.golden.update");
    private static final ObjectMapper CANONICAL = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    RealmExportNormalizer normalizationService;

    @Autowired
    ObjectMapper objectMapper;

    static Stream<String> exports() {
        return Stream.of(
                "normalization/input/acme-26.5.3.json",
                "normalization/input/acme-26.0.5.json",
                "import-files/exported-realm/26.3.3/master-realm.json",
                "import-files/exported-realm/26.0.5/master-realm.json",
                "import-files/exported-realm/25.0.5/master-realm.json",
                "import-files/exported-realm/19.0.3/master-realm.json");
    }

    @ParameterizedTest
    @MethodSource("exports")
    void normalizesTheExportAsExpected(String export) throws IOException {
        var realm = this.objectMapper.readValue(RESOURCES.resolve(export).toFile(), RealmRepresentation.class);

        var normalized = this.normalizationService.normalize(realm);

        var actual = canonical(this.objectMapper.valueToTree(normalized));
        var expectedFile = EXPECTED.resolve(export.replace('/', '_'));
        if (UPDATE) {
            Files.writeString(expectedFile, actual, StandardCharsets.UTF_8);
        }
        assertThat(actual).isEqualTo(Files.readString(expectedFile, StandardCharsets.UTF_8));
    }

    /** Sorted properties and array items, one property per line. */
    private static String canonical(JsonNode node) throws IOException {
        return CANONICAL.writeValueAsString(sorted(node)).replace("\r\n", "\n") + "\n";
    }

    private static JsonNode sorted(JsonNode node) throws IOException {
        if (node.isObject()) {
            var fields = new TreeMap<String, JsonNode>();
            for (var entry : node.properties()) {
                fields.put(entry.getKey(), sorted(entry.getValue()));
            }
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            fields.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            for (var item : node) {
                items.add(sorted(item));
            }
            List<String> keys = new ArrayList<>();
            for (var item : items) {
                keys.add(CANONICAL.writeValueAsString(item));
            }
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparing(keys::get));
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            order.forEach(i -> result.add(items.get(i)));
            return result;
        }
        return node;
    }
}
