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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The import files: read from their locations, then prepared. Preparing a file renders it as a Mustache template when
 * templating is enabled (ADR-007); it is left as it is otherwise, since a file may hold <code>{{</code> on purpose.
 */
public final class ImportFileReader implements ImportFileSource {

    private final FileLocations locations;
    private final Optional<MustacheTemplates> templates;

    /** @param templates the templates the files are rendered with; empty when templating is disabled */
    public ImportFileReader(final FileLocations locations, final Optional<MustacheTemplates> templates) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.templates = Objects.requireNonNull(templates, "templates");
    }

    @Override
    public List<ImportFile> read(final Collection<String> locations) {
        return this.locations.read(locations).stream().map(this::prepare).toList();
    }

    private ImportFile prepare(final ImportFile file) {
        return this.templates
                .map(templates ->
                        new ImportFile(file.location(), file.name(), templates.render(file.name(), file.content())))
                .orElse(file);
    }
}
