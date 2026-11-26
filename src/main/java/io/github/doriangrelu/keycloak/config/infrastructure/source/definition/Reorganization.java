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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reorganization of the files of a realm folder into the standard layout (ADR-008): the plan is computed from the
 * files only, and applying it recomputes the plan to refuse one that no longer matches the folder. Everything is
 * written in memory first, then to the files, and only then the files made useless are deleted.
 */
final class Reorganization {

    private static final String REALM_KEY = "realm";
    private static final String REALM_STEM = StandardLayout.fileStemOf(REALM_KEY);

    private final Path directory;
    /** The files, by name, in the order of their names. */
    private final Map<String, DocumentTree> trees = new LinkedHashMap<>();

    private final List<Move> moves = new ArrayList<>();
    private final List<Rename> renames = new ArrayList<>();
    private final List<String> removals = new ArrayList<>();

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
        Map<String, String> renamed = new HashMap<>();
        this.renames.forEach(rename -> renamed.put(rename.to(), rename.from()));
        Map<String, DocumentTree> created = new LinkedHashMap<>();
        Set<DocumentTree> touched = new LinkedHashSet<>();
        for (Move move : this.moves) {
            DocumentTree source = this.trees.get(move.from());
            DocumentTree target = target(move, source, renamed, created);
            TreeObject.Entry entry = source.root().entry(move.key()).orElseThrow();
            if (source.root().listSize(move.key()).isPresent()
                    && target.root().listSize(move.key()).isPresent()) {
                target.root().concat(move.key(), entry);
            } else {
                target.root().put(move.key(), entry);
            }
            source.root().remove(move.key());
            touched.add(source);
            touched.add(target);
        }
        Map<String, String> contents = new LinkedHashMap<>();
        this.trees.forEach((name, tree) -> {
            Optional<Rename> rename = this.renames.stream()
                    .filter(candidate -> candidate.from().equals(name))
                    .findFirst();
            if (touched.contains(tree) || rename.isPresent()) {
                if (!this.removals.contains(name)) {
                    contents.put(rename.map(Rename::to).orElse(name), tree.render());
                }
            }
        });
        created.forEach((name, tree) -> contents.put(name, tree.render()));
        contents.forEach((name, content) -> RealmDefinitionFiles.writeFile(this.directory.resolve(name), content));
        List<String> obsolete = new ArrayList<>(this.removals);
        this.renames.forEach(rename -> obsolete.add(rename.from()));
        obsolete.forEach(name -> delete(this.directory.resolve(name)));
        return List.copyOf(contents.keySet());
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
        String candidate = existing.containsKey(REALM_STEM) ? null : firstFileWithRealmKeys();
        Map<String, String> destinations = new HashMap<>(existing);
        if (candidate != null) {
            String renamed = REALM_STEM + this.trees.get(candidate).format().extension();
            this.renames.add(new Rename(candidate, renamed));
            destinations.put(REALM_STEM, renamed);
        }
        String created = newFileFormat().extension();
        Map<String, List<String>> occupants = new LinkedHashMap<>();
        Map<String, Set<String>> movedOut = new HashMap<>();
        this.trees.forEach((name, tree) -> {
            String home = name.equals(candidate) ? REALM_STEM : stemOf(name);
            for (String key : tree.root().keys()) {
                if (key.equals(REALM_KEY)) {
                    continue;
                }
                String stem = StandardLayout.fileStemOf(key);
                String destination = stem.equals(home) ? name : destinations.computeIfAbsent(stem, s -> s + created);
                if (!stem.equals(home)) {
                    this.moves.add(new Move(key, tree.root().listSize(key), name, destination));
                    movedOut.computeIfAbsent(name, n -> new LinkedHashSet<>()).add(key);
                }
                occupants
                        .computeIfAbsent(destination + "\0" + key, k -> new ArrayList<>())
                        .add(name);
            }
        });
        requireMergeable(occupants);
        movedOut.forEach((name, keys) -> {
            boolean emptied = this.trees.get(name).root().keys().stream()
                    .filter(key -> !key.equals(REALM_KEY))
                    .allMatch(keys::contains);
            if (emptied && !name.equals(candidate) && !stemOf(name).equals(REALM_STEM)) {
                this.removals.add(name);
            }
        });
    }

    /** A key written twice in the same file once reorganized must be a list: the items are put one after the other. */
    private void requireMergeable(final Map<String, List<String>> occupants) {
        occupants.forEach((destinationAndKey, names) -> {
            if (names.size() < 2) {
                return;
            }
            String key = destinationAndKey.substring(destinationAndKey.indexOf('\0') + 1);
            String destination = destinationAndKey.substring(0, destinationAndKey.indexOf('\0'));
            boolean allLists = names.stream()
                    .allMatch(name -> this.trees.get(name).root().listSize(key).isPresent());
            if (!allLists) {
                throw new ReorganizationRefusedException(
                        "The key '" + key + "' is written in " + String.join(", ", names)
                                + " and cannot be merged into " + destination + ": keep it in one file");
            }
        });
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

    private String firstFileWithRealmKeys() {
        return this.trees.entrySet().stream()
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
