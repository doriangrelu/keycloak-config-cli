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
import io.github.doriangrelu.keycloak.config.domain.definition.Origin;
import io.github.doriangrelu.keycloak.config.domain.definition.RealmDefinition;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.BuiltIns;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.ElementKeys;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.Finding;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RealmContext;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleElement;
import io.github.doriangrelu.keycloak.config.domain.definition.rules.RuleEngine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Lists the realms of the root and opens one (ADR-008). */
public final class ReadRealms {

    private final RealmDefinitions definitions;
    private final RuleBook rules;

    public ReadRealms(final RealmDefinitions definitions, final RuleBook rules) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public List<String> list() {
        return this.definitions.folders();
    }

    /** @throws UnknownRealmFolderException when no folder has this name */
    public RealmDefinition open(final String folder) {
        return this.definitions.load(folder).orElseThrow(() -> new UnknownRealmFolderException(folder));
    }

    /**
     * What the rules say about a realm as it is, even when it is read-only: the non-conformities of each element and of
     * the realm, and the rules the organisation switched off.
     *
     * @throws UnknownRealmFolderException when no folder has this name
     */
    public RealmCheck check(final String folder) {
        RealmDefinition realm = open(folder);
        RuleEngine engine = this.rules.engine();
        BuiltIns builtIns = this.rules.builtIns();
        Map<Origin, RuleElement> elements = new LinkedHashMap<>();
        realm.clients().forEach(defined -> elements.put(defined.origin(), RuleElement.CLIENT));
        realm.roles().forEach(defined -> elements.put(defined.origin(), RuleElement.ROLE));
        realm.groups().forEach(defined -> elements.put(defined.origin(), RuleElement.GROUP));
        Map<Origin, List<Finding>> findings = new LinkedHashMap<>();
        for (Map.Entry<Origin, ElementKeys> entry :
                this.definitions.elementKeys(folder).entrySet()) {
            RuleElement element = elements.get(entry.getKey());
            if (element == null) {
                continue;
            }
            List<Finding> found = engine.report(
                    element, entry.getValue(), new RealmContext(realm, Optional.of(entry.getKey()), builtIns));
            if (!found.isEmpty()) {
                findings.put(entry.getKey(), found);
            }
        }
        List<Finding> realmFindings = engine.report(
                RuleElement.REALM,
                this.definitions.realmKeys(folder),
                new RealmContext(realm, Optional.empty(), builtIns));
        return new RealmCheck(this.rules.rules().disablements(), findings, realmFindings);
    }
}
