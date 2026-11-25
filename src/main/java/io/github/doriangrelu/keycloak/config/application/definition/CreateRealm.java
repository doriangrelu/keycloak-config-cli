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

import io.github.doriangrelu.keycloak.config.application.definition.port.RealmDefinitions;
import io.github.doriangrelu.keycloak.config.domain.definition.DefinitionFormat;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.TemplatedValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyPath;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.KeyValue;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmContext;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleEngine;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Verdict;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Creates the folder of a new realm, with its realm file. */
public final class CreateRealm {

    private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9_-])?");

    private final RealmDefinitions definitions;
    private final RuleBook rules;

    public CreateRealm(final RealmDefinitions definitions, final RuleBook rules) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Checks that a folder can be created: a plain name that does not exist yet, whatever its case.
     *
     * @throws InvalidRealmFolderException when the folder is not a plain name
     * @throws RealmFolderExistsException when the folder exists already
     */
    public void checkFolder(final String folder) {
        if (folder == null || !FOLDER.matcher(folder).matches()) {
            throw new InvalidRealmFolderException(folder);
        }
        if (this.definitions.folders().stream()
                .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(folder.toLowerCase(Locale.ROOT)))) {
            throw new RealmFolderExistsException(folder);
        }
    }

    /**
     * @return the name of the realm file written, in the folder
     * @throws InvalidRealmFolderException when the folder is not a plain name
     * @throws RealmFolderExistsException when the folder exists already
     * @throws RuleViolationException when the new realm fails a required rule: nothing is written
     */
    public String create(final String folder, final TemplatedValue realm, final DefinitionFormat format) {
        checkFolder(folder);
        RuleEngine engine = this.rules.engine();
        ElementKeys base = new ElementKeys(Map.of(
                KeyPath.parse("realm"), new KeyValue.Scalar(realm),
                KeyPath.parse("enabled"), new KeyValue.Scalar(TemplatedValue.of("true"))));
        ElementKeys added = engine.defaults(RuleElement.REALM, base);
        RealmDefinition definition =
                new RealmDefinition(folder, Optional.of(realm), List.of(), List.of(), List.of(), Set.of(), List.of());
        Verdict verdict = engine.check(
                RuleElement.REALM,
                Optional.empty(),
                base.with(added),
                new RealmContext(definition, Optional.empty(), this.rules.builtIns()));
        if (!verdict.allowsWriting()) {
            throw new RuleViolationException(verdict.blocking());
        }
        return this.definitions.createRealm(folder, realm, format, added);
    }
}
