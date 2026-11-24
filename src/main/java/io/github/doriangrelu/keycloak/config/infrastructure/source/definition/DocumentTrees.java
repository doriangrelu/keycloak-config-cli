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
import java.util.regex.Pattern;

/** Reads definition files as trees, before rendering. */
public final class DocumentTrees {

    private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern YAML_COMMENT = Pattern.compile("(^|\\s)#.*$");

    private DocumentTrees() {}

    /**
     * @param fileName the name of the file, whose extension gives the format
     * @throws UnreadableDocumentException when the content is not valid before rendering
     */
    public static DocumentTree parse(final String fileName, final String content) {
        DefinitionFormat format = DefinitionFormat.ofFileName(fileName)
                .orElseThrow(() -> new IllegalArgumentException("Not a definition file: " + fileName));
        requireQuotedPlaceholders(content, format);
        return format == DefinitionFormat.YAML ? YamlDocumentTree.parse(content) : JsonDocumentTree.parse(content);
    }

    /**
     * A placeholder outside quotes makes the file invalid before rendering, or, in YAML, silently turns it into
     * nested mappings: it must be refused before parsing.
     */
    private static void requireQuotedPlaceholders(final String content, final DefinitionFormat format) {
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = SINGLE_QUOTED
                    .matcher(DOUBLE_QUOTED.matcher(lines[i]).replaceAll("\"\""))
                    .replaceAll("''");
            if (format == DefinitionFormat.YAML) {
                line = YAML_COMMENT.matcher(line).replaceAll("");
            }
            if (line.contains("{{")) {
                throw new UnreadableDocumentException(
                        i + 1, "a placeholder without quotes cannot be read before rendering: quote the value");
            }
        }
    }
}
