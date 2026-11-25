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

import io.github.doriangrelu.keycloak.config.domain.definition.ClientFlows;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The keys of the elements as the rules see them (ADR-009): nested objects flattened into key paths, lists of scalars
 * as {@link KeyValue.Listed}, lists of objects as {@link KeyValue.Shapeless}. Keys are relative to the object of the
 * element. Also writes the keys the rules add to an element.
 */
final class KeyViews {

    private static final List<String> FLOW_KEYS = List.of(
            "publicClient",
            "standardFlowEnabled",
            "implicitFlowEnabled",
            "directAccessGrantsEnabled",
            "serviceAccountsEnabled");

    private KeyViews() {}

    /** A client: its keys, the five flows always present with their effective value. */
    static ElementKeys client(final TreeObject client) {
        Map<KeyPath, KeyValue> keys = flatten(client, Set.of());
        ClientFlows defaults = ClientFlows.KEYCLOAK_DEFAULTS;
        List<Boolean> effective = List.of(
                defaults.publicClient(),
                defaults.standardFlow(),
                defaults.implicitFlow(),
                defaults.directAccessGrants(),
                defaults.serviceAccounts());
        for (int i = 0; i < FLOW_KEYS.size(); i++) {
            keys.putIfAbsent(
                    new KeyPath(List.of(FLOW_KEYS.get(i))),
                    new KeyValue.Scalar(TemplatedValue.of(String.valueOf(effective.get(i)))));
        }
        return new ElementKeys(keys);
    }

    /** A role: its keys, and {@code client}, the identifier of its client, for a client role. */
    static ElementKeys role(final TreeObject role, final Optional<String> clientId) {
        Map<KeyPath, KeyValue> keys = flatten(role, Set.of());
        clientId.ifPresent(id -> keys.put(new KeyPath(List.of("client")), new KeyValue.Scalar(TemplatedValue.of(id))));
        return new ElementKeys(keys);
    }

    /** A group: its keys without the sub-groups, and its {@code path}. */
    static ElementKeys group(final TreeObject group, final String path) {
        Map<KeyPath, KeyValue> keys = flatten(group, Set.of("subGroups"));
        keys.put(new KeyPath(List.of("path")), new KeyValue.Scalar(TemplatedValue.of(path)));
        return new ElementKeys(keys);
    }

    /** The top-level keys of a realm file, without the elements and the other lists of objects. */
    static Map<KeyPath, KeyValue> realm(final TreeObject document) {
        Set<String> elements = Set.of(ElementType.CLIENT.key(), ElementType.ROLE.key(), ElementType.GROUP.key());
        Map<KeyPath, KeyValue> keys = new LinkedHashMap<>();
        for (String key : document.keys()) {
            if (!elements.contains(key) && document.shape(key) != TreeObject.Shape.OBJECTS) {
                flattenKey(document, key, List.of(), keys);
            }
        }
        return keys;
    }

    /** The view of each element of a file, at the position the file is read at. */
    static Map<Origin, ElementKeys> elements(final String file, final TreeObject document) {
        Map<Origin, ElementKeys> elements = new LinkedHashMap<>();
        for (TreeObject client : document.objects(ElementType.CLIENT.key())) {
            elements.put(origin(file, client), client(client));
        }
        document.object(ElementType.ROLE.key()).ifPresent(roles -> {
            for (TreeObject role : roles.objects("realm")) {
                elements.put(origin(file, role), role(role, Optional.empty()));
            }
            roles.object("client")
                    .ifPresent(clients -> clients.objectLists().forEach((clientId, list) -> {
                        for (TreeObject role : list) {
                            elements.put(origin(file, role), role(role, Optional.of(clientId)));
                        }
                    }));
        });
        groups(file, document.objects(ElementType.GROUP.key()), "", elements);
        return elements;
    }

    /**
     * Writes the keys added by the rules, after the keys of the element: a key already written is never overwritten,
     * and the parents of a nested key are created. A key whose parent is not an object is left out.
     */
    static void write(final TreeObject element, final ElementKeys added) {
        added.values().forEach((path, value) -> {
            if (value instanceof KeyValue.Shapeless) {
                return;
            }
            TreeObject holder = element;
            List<String> segments = path.segments();
            for (String parent : segments.subList(0, segments.size() - 1)) {
                TreeObject.Shape shape = holder.shape(parent);
                if (shape == TreeObject.Shape.NONE) {
                    holder = holder.objectOrCreate(parent);
                } else if (shape == TreeObject.Shape.OBJECT) {
                    holder = holder.object(parent).orElseThrow();
                } else {
                    return;
                }
            }
            String last = segments.getLast();
            if (holder.shape(last) != TreeObject.Shape.NONE) {
                return;
            }
            switch (value) {
                case KeyValue.Scalar scalar ->
                    holder.setScalar(last, scalar.value().raw());
                case KeyValue.Listed listed -> holder.setTexts(last, listed.values());
                case KeyValue.Shapeless ignored -> {}
            }
        });
    }

    private static void groups(
            final String file,
            final List<TreeObject> groups,
            final String parentPath,
            final Map<Origin, ElementKeys> elements) {
        for (TreeObject group : groups) {
            String path = parentPath + "/" + group.text("name").orElse("");
            elements.put(origin(file, group), group(group, path));
            groups(file, group.objects("subGroups"), path, elements);
        }
    }

    private static Origin origin(final String file, final TreeObject object) {
        return new Origin(file, object.line(), object.column());
    }

    private static Map<KeyPath, KeyValue> flatten(final TreeObject object, final Set<String> skipped) {
        Map<KeyPath, KeyValue> keys = new LinkedHashMap<>();
        for (String key : object.keys()) {
            if (!skipped.contains(key)) {
                flattenKey(object, key, List.of(), keys);
            }
        }
        return keys;
    }

    private static void flattenKey(
            final TreeObject object, final String key, final List<String> prefix, final Map<KeyPath, KeyValue> keys) {
        List<String> segments = new ArrayList<>(prefix);
        segments.add(key);
        switch (object.shape(key)) {
            case NONE -> {}
            case SCALAR ->
                keys.put(
                        new KeyPath(segments),
                        new KeyValue.Scalar(TemplatedValue.of(object.text(key).orElse(""))));
            case SCALARS ->
                keys.put(
                        new KeyPath(segments),
                        new KeyValue.Listed(object.texts(key).stream()
                                .map(TemplatedValue::of)
                                .toList()));
            case OBJECTS -> keys.put(new KeyPath(segments), new KeyValue.Shapeless());
            case OBJECT -> {
                TreeObject child = object.object(key).orElseThrow();
                if (child.keys().isEmpty()) {
                    keys.put(new KeyPath(segments), new KeyValue.Shapeless());
                }
                for (String name : child.keys()) {
                    flattenKey(child, name, segments, keys);
                }
            }
        }
    }
}
