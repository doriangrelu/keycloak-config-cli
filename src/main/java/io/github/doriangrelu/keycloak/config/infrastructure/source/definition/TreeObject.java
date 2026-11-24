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

import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An object of a definition file, in YAML or JSON. A value of another kind than the one asked for is treated as absent.
 */
public interface TreeObject {

    /** A scalar value, as written. */
    Optional<String> text(String key);

    /** A boolean, written {@code true} or {@code false}. */
    Optional<Boolean> bool(String key);

    /** A list of scalars; empty when absent. */
    List<String> texts(String key);

    Optional<TreeObject> object(String key);

    /** A list of objects; empty when absent. */
    List<TreeObject> objects(String key);

    /** An object of scalars, such as {@code attributes}; empty when absent. */
    Map<String, String> textEntries(String key);

    /** An object of lists of scalars, such as the {@code clientRoles} of a group; empty when absent. */
    Map<String, List<String>> textLists(String key);

    /** The entries of this object that are lists of objects, such as {@code roles.client}. */
    Map<String, List<TreeObject>> objectLists();

    boolean has(String key);

    /** The first line of this object, from 1. */
    int line();

    /** Writes a scalar; a value holding a placeholder is written between double quotes. Unchanged when equal. */
    void setText(String key, TemplatedValue value);

    /** Writes a boolean. Unchanged when equal. */
    void setBool(String key, boolean value);

    /** Writes a list of scalars, in the style of the list it replaces. Unchanged when equal. */
    void setTexts(String key, List<TemplatedValue> values);

    void remove(String key);

    /** The object of a key, created empty when absent. */
    TreeObject objectOrCreate(String key);

    /** Adds an empty object at the end of a list, created when absent. */
    TreeObject append(String listKey);
}
