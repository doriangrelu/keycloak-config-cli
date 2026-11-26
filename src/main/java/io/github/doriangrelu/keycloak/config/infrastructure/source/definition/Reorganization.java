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

import io.github.doriangrelu.keycloak.config.application.definition.ReorganizationRefusedException;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan.Move;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan.Rename;
import io.github.doriangrelu.keycloak.config.domain.definition.StandardLayout;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The reorganization of the files of a realm folder into the standard layout (ADR-008): the plan is computed from the
 * files only, and applying it recomputes the plan to refuse one that no longer matches the folder.
 *
 * <p>Every new content is rendered in memory, then written to a temporary file of the folder, then moved into place,
 * and only then the files made useless are deleted. A failure while preparing leaves the folder untouched. The window
 * that remains is the moves into place: a failure there leaves some files already replaced, which Git restores.
 *
 * <p>A key written in several files for the same destination is merged: lists are concatenated in the order of the
 * file names, objects are merged entry by entry (as {@code roles}); a value that differs on both sides is refused. A
 * merged key is rebuilt from its content, so its comments are not kept.
 */
final class Reorganization {

    private static final String REALM_KEY = "realm";
    private static final String REALM_STEM = StandardLayout.fileStemOf(REALM_KEY);
    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path directory;
    /** The files, by name, in the order of their names. */
    private final Map<String, DocumentTree> trees = new LinkedHashMap<>();

    private final List<Move> moves = new ArrayList<>();
    private final List<Rename> renames = new ArrayList<>();
    private final List<String> removals = new ArrayList<>();
    /** The files that hold a key once reorganized, by destination file and key, in the order of the file names. */
    private final Map<Slot, List<String>> occupants = new LinkedHashMap<>();

    private record Slot(String destination, String key) {}

    private record PlainEntry(Object plain) implements TreeObject.Entry {}

    Reorganization(final Path directory) {
        this.directory = directory;
        for (Path file : RealmDefinitionFiles.definitionFiles(directory)) {
            String name = file.getFileName().toString();
            this.trees.put(name, DocumentTrees.parse(name, RealmDefinitionFiles.readFile(file)));
        }
        compute();
    }

    ReorganizationPlan plan() {
        return new ReorganizationPlan(this.moves, this.renames, this.removals);
    }

    /**
     * Writes the plan.
     *
     * @return the files written
     * @throws ReorganizationRefusedException when the plan is not the one of the files as they are now
     */
    List<String> apply(final ReorganizationPlan plan) {
        if (!plan().equals(plan)) {
            throw new ReorganizationRefusedException(
                    "The reorganization plan is out of date: the files of the realm changed since it was made");
        }
        Map<Slot, Object> merged = new HashMap<>();
        this.occupants.forEach((slot, names) -> {
            if (names.size() > 1) {
                merged.put(slot, mergedContent(slot, names));
            }
        });
        Map<String, String> renamed = new HashMap<>();
        this.renames.forEach(rename -> renamed.put(rename.to(), rename.from()));
        Map<String, DocumentTree> created = new LinkedHashMap<>();
        Set<DocumentTree> touched = new LinkedHashSet<>();
        for (Move move : this.moves) {
            DocumentTree source = this.trees.get(move.from());
            DocumentTree target = target(move, source, renamed, created);
            Object content = merged.get(new Slot(move.to(), move.key()));
            target.root()
                    .put(
                            move.key(),
                            content == null ? source.root().entry(move.key()).orElseThrow() : new PlainEntry(content));
            touched.add(source);
            touched.add(target);
        }
        this.moves.forEach(move -> this.trees.get(move.from()).root().remove(move.key()));
        Map<String, String> contents = new LinkedHashMap<>();
        this.trees.forEach((name, tree) -> {
            Optional<Rename> rename = this.renames.stream()
                    .filter(candidate -> candidate.from().equals(name))
                    .findFirst();
            if ((touched.contains(tree) || rename.isPresent()) && !this.removals.contains(name)) {
                contents.put(rename.map(Rename::to).orElse(name), tree.render());
            }
        });
        created.forEach((name, tree) -> contents.put(name, tree.render()));
        putInPlace(contents);
        List<String> obsolete = new ArrayList<>(this.removals);
        this.renames.forEach(rename -> obsolete.add(rename.from()));
        obsolete.forEach(name -> delete(this.directory.resolve(name)));
        return List.copyOf(contents.keySet());
    }

    /** Writes every content to a temporary file, then moves each into place; nothing is replaced before all exist. */
    private void putInPlace(final Map<String, String> contents) {
        Map<Path, Path> temporaries = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : contents.entrySet()) {
                Path temporary = this.directory.resolve("." + entry.getKey() + TEMPORARY_SUFFIX);
                temporaries.put(temporary, this.directory.resolve(entry.getKey()));
                RealmDefinitionFiles.writeFile(temporary, entry.getValue());
            }
            for (Map.Entry<Path, Path> entry : temporaries.entrySet()) {
                move(entry.getKey(), entry.getValue());
            }
        } finally {
            temporaries.keySet().forEach(Reorganization::delete);
        }
    }

    private static void move(final Path from, final Path to) {
        try {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DocumentTree target(
            final Move move,
            final DocumentTree source,
            final Map<String, String> renamed,
            final Map<String, DocumentTree> created) {
        if (this.trees.containsKey(move.to())) {
            return this.trees.get(move.to());
        }
        if (renamed.containsKey(move.to())) {
            return this.trees.get(renamed.get(move.to()));
        }
        return created.computeIfAbsent(move.to(), name -> {
            DocumentTree tree = DocumentTrees.parse(name, "");
            Optional<String> realm = source.root().text(REALM_KEY).or(this::anyRealm);
            realm.ifPresent(value -> tree.root().setText(REALM_KEY, TemplatedValue.of(value)));
            return tree;
        });
    }

    private Optional<String> anyRealm() {
        return this.trees.values().stream()
                .map(tree -> tree.root().text(REALM_KEY))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static void delete(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void compute() {
        Map<String, String> existing = new HashMap<>();
        this.trees.keySet().forEach(name -> {
            if (StandardLayout.stems().contains(stemOf(name))) {
                existing.putIfAbsent(stemOf(name), name);
            }
        });
        // Only a file with a name of its own is renamed to the realm file: a standard file keeps its name and its keys
        String candidate = existing.containsKey(REALM_STEM) ? null : firstOtherFileWithRealmKeys();
        Map<String, String> destinations = new HashMap<>(existing);
        if (candidate != null) {
            String renamed = REALM_STEM + this.trees.get(candidate).format().extension();
            this.renames.add(new Rename(candidate, renamed));
            destinations.put(REALM_STEM, renamed);
        }
        String created = newFileFormat().extension();
        Map<String, Set<String>> movedOut = new HashMap<>();
        Set<String> receiving = new LinkedHashSet<>();
        this.trees.forEach((name, tree) -> {
            String home = name.equals(candidate) ? REALM_STEM : stemOf(name);
            for (String key : tree.root().keys()) {
                if (key.equals(REALM_KEY)) {
                    continue;
                }
                String stem = StandardLayout.fileStemOf(key);
                boolean stays = stem.equals(home);
                // The keys of the file renamed to the realm file meet the ones moved there, under its final name
                String destination = !stays
                        ? destinations.computeIfAbsent(stem, s -> s + created)
                        : name.equals(candidate) ? destinations.get(REALM_STEM) : name;
                if (!stays) {
                    this.moves.add(new Move(key, tree.root().listSize(key), name, destination));
                    movedOut.computeIfAbsent(name, n -> new LinkedHashSet<>()).add(key);
                    receiving.add(destination);
                }
                this.occupants
                        .computeIfAbsent(new Slot(destination, key), k -> new ArrayList<>())
                        .add(name);
            }
        });
        this.occupants.forEach((slot, names) -> {
            if (names.size() > 1) {
                mergedContent(slot, names);
            }
        });
        List<String> emptied = new ArrayList<>();
        this.trees.forEach((name, tree) -> {
            Set<String> out = movedOut.getOrDefault(name, Set.of());
            boolean onlyRealm = tree.root().keys().stream()
                    .filter(key -> !key.equals(REALM_KEY))
                    .allMatch(out::contains);
            if (onlyRealm && !name.equals(candidate) && !stemOf(name).equals(REALM_STEM) && !receiving.contains(name)) {
                emptied.add(name);
            }
        });
        // The realm name lives in the files: the last file of a realm is kept
        if (!emptied.isEmpty()
                && emptied.size() == this.trees.size()
                && this.moves.isEmpty()
                && this.renames.isEmpty()) {
            emptied.removeFirst();
        }
        this.removals.addAll(emptied);
    }

    /** The content of a key written in several files for the same destination, the files in the order of their names. */
    private Object mergedContent(final Slot slot, final List<String> names) {
        Object merged = null;
        boolean first = true;
        for (String name : names) {
            Object content =
                    this.trees.get(name).root().entry(slot.key()).orElseThrow().plain();
            if (first) {
                merged = content;
                first = false;
            } else {
                merged = merge(merged, content, slot.key(), slot, names);
            }
        }
        return merged;
    }

    private static Object merge(
            final Object left, final Object right, final String path, final Slot slot, final List<String> names) {
        if (left instanceof List<?> first && right instanceof List<?> second) {
            List<Object> all = new ArrayList<>(first);
            all.addAll(second);
            return all;
        }
        if (left instanceof Map<?, ?> first && right instanceof Map<?, ?> second) {
            Map<Object, Object> all = new LinkedHashMap<>(first);
            second.forEach((name, value) -> {
                Object current = all.get(name);
                // A null is an absent value
                all.put(name, current == null ? value : merge(current, value, path + "." + name, slot, names));
            });
            return all;
        }
        if (left == null || right == null) {
            return left == null ? right : left;
        }
        if (Objects.equals(left, right)) {
            return left;
        }
        throw new ReorganizationRefusedException("The key '" + path + "' is written with different values in "
                + String.join(", ", names) + " and cannot be merged into " + slot.destination()
                + ": keep it in one file");
    }

    /** The format of the new files: the one of the file with the most keys of the realm file, else of the first file. */
    private DefinitionFormat newFileFormat() {
        DefinitionFormat best = null;
        long most = -1;
        for (DocumentTree tree : this.trees.values()) {
            long count = realmKeys(tree).size();
            if (count > most) {
                most = count;
                best = tree.format();
            }
        }
        return best == null ? DefinitionFormat.YAML : best;
    }

    private String firstOtherFileWithRealmKeys() {
        return this.trees.entrySet().stream()
                .filter(entry -> !StandardLayout.stems().contains(stemOf(entry.getKey())))
                .filter(entry -> !realmKeys(entry.getValue()).isEmpty())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static List<String> realmKeys(final DocumentTree tree) {
        return tree.root().keys().stream()
                .filter(key -> !key.equals(REALM_KEY))
                .filter(key -> StandardLayout.fileStemOf(key).equals(REALM_STEM))
                .toList();
    }

    private static String stemOf(final String name) {
        return name.substring(0, name.lastIndexOf('.'));
    }
}
