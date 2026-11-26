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

package io.github.doriangrelu.keycloak.config.domain.definition;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * What reorganizing a realm would do: the top-level keys moved to their standard file, the files renamed to their
 * standard name, and the files left empty that would be removed.
 */
public record ReorganizationPlan(List<Move> moves, List<Rename> renames, List<String> removals) {

    public ReorganizationPlan {
        moves = List.copyOf(Objects.requireNonNull(moves, "moves"));
        renames = List.copyOf(Objects.requireNonNull(renames, "renames"));
        removals = List.copyOf(Objects.requireNonNull(removals, "removals"));
    }

    /** A top-level key moved from a file to another; {@code count} is the number of items of a list. */
    public record Move(String key, OptionalInt count, String from, String to) {}

    /** A file renamed. */
    public record Rename(String from, String to) {}

    /** Whether the realm is already in the standard layout. */
    public boolean isEmpty() {
        return this.moves.isEmpty() && this.renames.isEmpty() && this.removals.isEmpty();
    }
}
