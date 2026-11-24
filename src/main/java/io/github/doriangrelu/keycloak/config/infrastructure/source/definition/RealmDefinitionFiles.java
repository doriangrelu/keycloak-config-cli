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

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.common.DomainException;
import io.github.doriangrelu.keycloak.config.domain.definition.ClientDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Defined;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.ElementType;
import io.github.doriangrelu.keycloak.config.domain.definition.GroupDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.RoleDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.UnreadableFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
                        clients.add(new Defined<>(ClientKeys.read(client), new Origin(name, client.line())));
                    }
                }
                Optional<TreeObject> declaredRoles = document.object(ElementType.ROLE.key());
                if (declaredRoles.isPresent()
                        && (declaredRoles.get().has("realm")
                                || declaredRoles.get().has("client"))) {
                    declared.add(ElementType.ROLE);
                    roles.addAll(RoleKeys.read(declaredRoles.get(), name));
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
        return Optional.of(new RealmDefinition(folder, realm, clients, roles, groups, declared, unreadable));
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

    private static String readFile(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeFile(final Path file, final String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> definitionFiles(final Path directory) {
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
}
