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

package io.github.doriangrelu.keycloak.config.application.definition;

import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import java.util.Optional;

/** What the user is told, and asked to confirm, when an element is the first of its type in a realm. */
@FunctionalInterface
public interface DeclarationWarning {

    /** Without a target to simulate on: the general warning of {@link FirstDeclarationException#warning}. */
    DeclarationWarning OFFLINE = (folder, element) -> FirstDeclarationException.warning(element);

    /** The warning for this element, to be written in the realm of this folder. */
    String of(String folder, ElementDefinition element);

    /**
     * What the user is told when writing the first element of its type is confirmed in advance, such as with
     * {@code --declare-type}: nothing by default.
     */
    default Optional<String> information(final String folder, final ElementDefinition element) {
        return Optional.empty();
    }
}
