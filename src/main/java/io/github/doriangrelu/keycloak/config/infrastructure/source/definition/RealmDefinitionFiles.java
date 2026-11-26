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

import io.github.doriangrelu.keycloak.config.application.definition.RealmFolderExistsException;
import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.common.DomainException;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.ReorganizationPlan;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The realm definitions of a root folder: one folder per realm, its files split by type, in YAML or JSON (ADR-008).
 * Each file is read before rendering; a file that cannot be read is reported, and the others are read.
 */
public final class RealmDefinitionFiles implements RealmDefinitions {

    private static final int MAX_FORMAT_PASSES = 4;

    private final Path root;

    public RealmDefinitionFiles(final Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Override
    public List<String> folders() {
        if (!Files.isDirectory(this.root)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(this.root)) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<RealmDefinition> load(final String folder) {
        Path directory = this.root.resolve(folder);
        if (!Files.isDirectory(directory) || !folders().contains(folder)) {
            return Optional.empty();
        }
        Optional<TemplatedValue> realm = Optional.empty();
        List<Defined<ClientDefinition>> clients = new ArrayList<>();
        List<Defined<RoleDefinition>> roles = new ArrayList<>();
        List<Defined<GroupDefinition>> groups = new ArrayList<>();
        Set<ElementType> declared = EnumSet.noneOf(ElementType.class);
        boolean declaresRealmRoles = false;
        Set<String> declaredRoleClients = new LinkedHashSet<>();
        List<UnreadableFile> unreadable = new ArrayList<>();
        for (Path file : definitionFiles(directory)) {
            String name = file.getFileName().toString();
            try {
                TreeObject document = DocumentTrees.parse(name, Files.readString(file, StandardCharsets.UTF_8))
                        .root();
                if (realm.isEmpty()) {
                    realm = document.text("realm").map(TemplatedValue::of);
                }
                if (document.has(ElementType.CLIENT.key())) {
                    declared.add(ElementType.CLIENT);
                    for (TreeObject client : document.objects(ElementType.CLIENT.key())) {
                        clients.add(new Defined<>(
                                ClientKeys.read(client), new Origin(name, client.line(), client.column())));
                    }
                }
                if (declares(document, ElementType.ROLE)) {
                    declared.add(ElementType.ROLE);
                    TreeObject declaredRoles =
                            document.object(ElementType.ROLE.key()).orElseThrow();
                    declaresRealmRoles |= declaredRoles.has("realm");
                    declaredRoles
                            .object("client")
                            .ifPresent(roleClients -> declaredRoleClients.addAll(
                                    roleClients.objectLists().keySet()));
                    roles.addAll(RoleKeys.read(
                            document.object(ElementType.ROLE.key()).orElseThrow(), name));
                }
                if (document.has(ElementType.GROUP.key())) {
                    declared.add(ElementType.GROUP);
                    groups.addAll(GroupKeys.read(document.objects(ElementType.GROUP.key()), name));
                }
            } catch (UnreadableDocumentException e) {
                unreadable.add(new UnreadableFile(name, e.line(), e.reason()));
            } catch (DomainException e) {
                unreadable.add(new UnreadableFile(name, 1, e.getMessage()));
            } catch (IOException e) {
                unreadable.add(new UnreadableFile(name, 1, "cannot be read: " + e.getMessage()));
            }
        }
        return Optional.of(new RealmDefinition(
                folder, realm, clients, roles, groups, declared, declaresRealmRoles, declaredRoleClients, unreadable));
    }

    @Override
    public List<String> unformattedFiles(final String folder) {
        List<String> unformatted = new ArrayList<>();
        for (Path file : definitionFiles(this.root.resolve(folder))) {
            String name = file.getFileName().toString();
            String content = readFile(file);
            if (!DocumentTrees.parse(name, content).render().equals(content)) {
                unformatted.add(name);
            }
        }
        return unformatted;
    }

    @Override
    public List<String> format(final String folder) {
        List<String> changed = new ArrayList<>();
        for (Path file : definitionFiles(this.root.resolve(folder))) {
            String name = file.getFileName().toString();
            String original = readFile(file);
            String formatted = formatted(name, original);
            if (!formatted.equals(original)) {
                writeFile(file, formatted);
                changed.add(name);
            }
        }
        return changed;
    }

    @Override
    public Map<String, String> sources(final String folder) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path file : definitionFiles(this.root.resolve(folder))) {
            sources.put(file.getFileName().toString(), readFile(file));
        }
        return sources;
    }

    @Override
    public Optional<String> fileOf(final String folder, final ElementType type) {
        for (Path file : definitionFiles(this.root.resolve(folder))) {
            String name = file.getFileName().toString();
            try {
                if (declares(DocumentTrees.parse(name, readFile(file)).root(), type)) {
                    return Optional.of(name);
                }
            } catch (UnreadableDocumentException e) {
                // A file the shell cannot read holds nothing it can write to
            }
        }
        return Optional.empty();
    }

    @Override
    public String save(
            final String folder,
            final ElementDefinition definition,
            final Optional<Origin> existing,
            final ElementKeys added) {
        Edit edit = edit(folder, definition, existing, added, true);
        writeFile(this.root.resolve(folder).resolve(edit.name()), edit.tree().render());
        return edit.name();
    }

    @Override
    public ElementKeys preview(
            final String folder,
            final ElementDefinition definition,
            final Optional<Origin> existing,
            final ElementKeys added) {
        Edit edit = edit(folder, definition, existing, added, false);
        return switch (definition) {
            case ClientDefinition ignored -> KeyViews.client(edit.element());
            case RoleDefinition role ->
                KeyViews.role(edit.element(), role.clientId().map(TemplatedValue::raw));
            case GroupDefinition group -> KeyViews.group(edit.element(), group.path());
        };
    }

    @Override
    public Map<Origin, ElementKeys> elementKeys(final String folder) {
        Map<Origin, ElementKeys> keys = new LinkedHashMap<>();
        for (Path file : definitionFiles(this.root.resolve(folder))) {
            String name = file.getFileName().toString();
            try {
                keys.putAll(KeyViews.elements(
                        name, DocumentTrees.parse(name, readFile(file)).root()));
            } catch (UnreadableDocumentException e) {
                // A file that cannot be read has no element to show
            }
        }
        return keys;
    }

    @Override
    public ElementKeys realmKeys(final String folder) {
        Map<KeyPath, KeyValue> keys = new LinkedHashMap<>();
        for (Path file : definitionFiles(this.root.resolve(folder))) {
            String name = file.getFileName().toString();
            try {
                KeyViews.realm(DocumentTrees.parse(name, readFile(file)).root()).forEach(keys::putIfAbsent);
            } catch (UnreadableDocumentException e) {
                // A file that cannot be read has no key to show
            }
        }
        return new ElementKeys(keys);
    }

    /**
     * An element written in the tree of its file, in memory: what {@link #save} writes and {@link #preview} shows. A
     * new element in a folder without a file for its type is written in a new document.
     */
    private Edit edit(
            final String folder,
            final ElementDefinition definition,
            final Optional<Origin> existing,
            final ElementKeys added,
            final boolean requireRealm) {
        Path directory = this.root.resolve(folder);
        String name = existing.map(Origin::file)
                .or(() -> fileOf(folder, definition.type()))
                .orElseGet(
                        () -> definition.type().fileName() + formatOf(directory).extension());
        Path file = directory.resolve(name);
        DocumentTree tree;
        if (Files.exists(file)) {
            tree = DocumentTrees.parse(name, readFile(file));
        } else {
            tree = DocumentTrees.parse(name, "");
            Optional<TemplatedValue> realm = load(folder).flatMap(RealmDefinition::realm);
            if (requireRealm && realm.isEmpty()) {
                throw new IllegalStateException("No file of realm '" + folder + "' gives its name");
            }
            realm.ifPresent(value -> tree.root().setText("realm", value));
        }
        TreeObject root = tree.root();
        TreeObject element = switch (definition) {
            case ClientDefinition client -> {
                TreeObject object = existingOrNew(root, ElementType.CLIENT.key(), existing);
                ClientKeys.write(client, object);
                yield object;
            }
            case RoleDefinition role -> RoleKeys.write(role, root.objectOrCreate(ElementType.ROLE.key()), existing);
            case GroupDefinition group -> GroupKeys.write(group, root, existing);
        };
        KeyViews.write(element, added);
        return new Edit(name, tree, element);
    }

    private record Edit(String name, DocumentTree tree, TreeObject element) {}

    @Override
    public String createRealm(
            final String folder, final TemplatedValue realm, final DefinitionFormat format, final ElementKeys added) {
        String name = "00-realm" + format.extension();
        DocumentTree tree = DocumentTrees.parse(name, "");
        tree.root().setText("realm", realm);
        tree.root().setBool("enabled", true);
        KeyViews.write(tree.root(), added);
        try {
            Files.createDirectories(this.root);
            Files.createDirectory(this.root.resolve(folder));
        } catch (FileAlreadyExistsException e) {
            throw new RealmFolderExistsException(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        writeFile(this.root.resolve(folder).resolve(name), tree.render());
        return name;
    }

    private static TreeObject existingOrNew(
            final TreeObject root, final String listKey, final Optional<Origin> existing) {
        return existing.flatMap(at -> root.objects(listKey).stream()
                        .filter(found -> found.isAt(at))
                        .findFirst())
                .orElseGet(() -> root.append(listKey));
    }

    private static boolean declares(final TreeObject document, final ElementType type) {
        return switch (type) {
            case CLIENT, GROUP -> document.has(type.key());
            case ROLE ->
                document.object(type.key())
                        .filter(roles -> roles.has("realm") || roles.has("client"))
                        .isPresent();
        };
    }

    /** The format of the realm: the one of its {@code 00-realm} file, else of its first file, else YAML. */
    private static DefinitionFormat formatOf(final Path directory) {
        List<Path> files = definitionFiles(directory);
        return files.stream()
                .filter(file -> file.getFileName().toString().startsWith("00-realm."))
                .findFirst()
                .or(() -> files.stream().findFirst())
                .flatMap(file -> DefinitionFormat.ofFileName(file.getFileName().toString()))
                .orElse(DefinitionFormat.YAML);
    }

    /**
     * The layout of keycloak-config-cli is reached after at most a few rewrites: SnakeYAML moves a comment above a list
     * item into the item on the first one, and keeps it there on the next ones.
     */
    private static String formatted(final String name, final String original) {
        String current = original;
        for (int pass = 0; pass < MAX_FORMAT_PASSES; pass++) {
            String rendered = DocumentTrees.parse(name, current).render();
            if (rendered.equals(current)) {
                requireSameContent(name, original, current);
                return current;
            }
            current = rendered;
        }
        throw new IllegalStateException(
                "The layout of " + name + " is not stable after " + MAX_FORMAT_PASSES + " rewrites");
    }

    private static void requireSameContent(final String name, final String original, final String formatted) {
        if (!PlainContent.of(name, original).equals(PlainContent.of(name, formatted))) {
            throw new IllegalStateException("Formatting " + name + " would change its content");
        }
    }

    static String readFile(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void writeFile(final Path file, final String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Path> definitionFiles(final Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> DefinitionFormat.ofFileName(
                                    file.getFileName().toString())
                            .isPresent())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ReorganizationPlan planReorganization(final String folder) {
        return new Reorganization(this.root.resolve(folder)).plan();
    }

    @Override
    public List<String> reorganize(final String folder, final ReorganizationPlan plan) {
        return new Reorganization(this.root.resolve(folder)).apply(plan);
    }
}
