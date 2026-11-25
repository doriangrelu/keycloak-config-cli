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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The path of a key in an exported element ({@code attributes."pkce.code.challenge.method"}): segments separated by
 * dots; a segment between double quotes may hold dots.
 *
 * @param segments the segments, never empty
 */
public record KeyPath(List<String> segments) {

    public KeyPath {
        segments = List.copyOf(segments);
        if (segments.isEmpty() || segments.stream().anyMatch(String::isEmpty)) {
            throw new InvalidRulesException("A key path needs at least one segment and no empty segment");
        }
    }

    /**
     * @throws InvalidRulesException when the text is empty, has an empty segment or an unclosed quote
     */
    public static KeyPath parse(final String text) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '.' && !quoted) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new InvalidRulesException("The key path '" + text + "' has an unclosed quote");
        }
        segments.add(current.toString());
        if (segments.stream().anyMatch(String::isEmpty)) {
            throw new InvalidRulesException("The key path '" + text + "' has an empty segment");
        }
        return new KeyPath(segments);
    }

    @Override
    public String toString() {
        return this.segments.stream()
                .map(segment -> segment.contains(".") ? "\"" + segment + "\"" : segment)
                .collect(Collectors.joining("."));
    }
}
