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

package io.github.doriangrelu.keycloak.config.domain.common;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Protects resources from deletion, declared by {@code protectedResources} in an import file. A protected resource is
 * never deleted, whatever the managed mode.
 *
 * @param matching how {@code matcher} is compared to the resource name
 * @param matcher the exact name, or a regular expression matching the whole name
 */
public record ProtectionRule(Matching matching, String matcher) {

    public enum Matching {
        EQUALS,
        PATTERN
    }

    public ProtectionRule {
        Objects.requireNonNull(matching, "matching");
        Objects.requireNonNull(matcher, "matcher");
        if (matching == Matching.PATTERN) {
            Pattern.compile(matcher);
        }
    }

    public boolean protects(final String name) {
        if (name == null) {
            return false;
        }
        return switch (this.matching) {
            case EQUALS -> this.matcher.equals(name);
            case PATTERN -> Pattern.matches(this.matcher, name);
        };
    }
}
