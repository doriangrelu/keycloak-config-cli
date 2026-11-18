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

package io.github.doriangrelu.keycloak.config.service.state;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tracks, for one run, what the import files declare and what the remote state contained before the run. The
 * clean phase uses it to delete only what it is allowed to:
 *
 * <ul>
 *   <li>a resource type is cleaned only if at least one file of the realm declares it, even an empty list;
 *   <li>with remote state enabled, only resources created by keycloak-config-cli are deleted. The state is captured
 *       before the import, because the import overwrites it before the clean phase runs.
 * </ul>
 *
 * <p>Transitional: roles already moved to the reconciliation engine (ADR-004); the other types follow.
 */
public final class ImportTracking {

    public static final String CLIENTS = "clients";
    public static final String GROUPS = "groups";

    private ImportTracking() {}

    /** Resource type declared by an import file, possibly with an empty list. */
    private record Declared(String type) {}

    /** Resource name recorded in the remote state before the run. */
    private record PreviousState(String type, String name) {}

    /** Marks that the remote state of a resource type was captured. */
    private record Captured(String type) {}

    public static void declare(final String realm, final String type) {
        ExecutionContextHolder.context().put(realm, Declared.class, List.of(new Declared(type)));
    }

    public static boolean isDeclared(final String realm, final String type) {
        return ExecutionContextHolder.context().get(realm, Declared.class).contains(new Declared(type));
    }

    public static boolean isStateCaptured(final String realm, final String type) {
        return ExecutionContextHolder.context().get(realm, Captured.class).contains(new Captured(type));
    }

    /** Records the state of a resource type the first time it is seen in the run. */
    public static void captureState(final String realm, final String type, final Collection<String> names) {
        if (isStateCaptured(realm, type)) {
            return;
        }
        ExecutionContextHolder.context()
                .put(
                        realm,
                        PreviousState.class,
                        names.stream()
                                .map(name -> new PreviousState(type, name))
                                .toList());
        ExecutionContextHolder.context().put(realm, Captured.class, List.of(new Captured(type)));
    }

    public static Set<String> previousState(final String realm, final String type) {
        return ExecutionContextHolder.context().get(realm, PreviousState.class).stream()
                .filter(entry -> entry.type().equals(type))
                .map(PreviousState::name)
                .collect(Collectors.toSet());
    }
}
