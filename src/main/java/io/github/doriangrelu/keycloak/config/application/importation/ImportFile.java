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

package io.github.doriangrelu.keycloak.config.application.importation;

import java.util.Objects;

/**
 * An import file once read and prepared (templates rendered), before it is parsed: a raw JSON or YAML document,
 * possibly holding several realms.
 *
 * @param location the configured location the file was found with, such as a pattern or a directory
 * @param name the file itself, as a URI
 * @param content the document
 */
public record ImportFile(String location, String name, String content) {

    public ImportFile {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
    }
}
