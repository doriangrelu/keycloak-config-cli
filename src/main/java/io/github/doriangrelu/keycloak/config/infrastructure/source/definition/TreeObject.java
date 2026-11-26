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

import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

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

    /** The keys of this object, in order. */
    List<String> keys();

    /** The kind of value under a key; a null value is {@link Shape#NONE}. */
    Shape shape(String key);

    /** The kind of value of a key. */
    enum Shape {
        /** Absent or null. */
        NONE,
        /** A text, number or boolean. */
        SCALAR,
        /** A list of scalars, possibly empty. */
        SCALARS,
        /** An object. */
        OBJECT,
        /** A list holding at least one object or list. */
        OBJECTS
    }

    /** The first line of this object, from 1. */
    int line();

    /** The first column of this object, from 1. */
    int column();

    /** Whether this object is the one written at this position. */
    default boolean isAt(final Origin origin) {
        return line() == origin.line() && column() == origin.column();
    }

    /** Writes a scalar; a value holding a placeholder is written between double quotes. Unchanged when equal. */
    void setText(String key, TemplatedValue value);

    /**
     * Writes a scalar given as text: {@code true} and {@code false} as a boolean, an integer as a number, anything else
     * as text. Placeholders are quoted.
     */
    void setScalar(String key, String text);

    /** Writes a boolean. Unchanged when equal. */
    void setBool(String key, boolean value);

    /** Writes a list of scalars, in the style of the list it replaces. Unchanged when equal. */
    void setTexts(String key, List<TemplatedValue> values);

    void remove(String key);

    /** The object of a key, created empty when absent. */
    TreeObject objectOrCreate(String key);

    /** Adds an empty object at the end of a list, created when absent. */
    TreeObject append(String listKey);

    /** The number of items of the list under a key; empty when the value is not a list. */
    OptionalInt listSize(String key);

    /**
     * A top-level entry of an object: its key, its comments where the format keeps them, and its value. It is moved as
     * a whole to an object of the same format, and rebuilt from its content in an object of the other format.
     */
    interface Entry {

        /** The value of the entry as plain Java values: maps, lists, text, numbers, booleans and null. */
        Object plain();
    }

    /** The entry of a key, without removing it. */
    Optional<Entry> entry(String key);

    /** Writes an entry under a key: replaces the current value, else adds it at the end. */
    void put(String key, Entry entry);

    /** Adds the items of a list entry at the end of the list under a key; writes the entry when the key is absent. */
    void concat(String key, Entry list);
}
