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

package io.github.doriangrelu.keycloak.config.domain.definition.rules;

/** A rule on the configuration of a realm: what it applies to, how it is reported and how an element is checked. */
public interface Rule {

    /** The identifier of the rule, such as {@code client.spa.pkce}. */
    String id();

    /** The kind of element the rule applies to. */
    RuleElement element();

    Severity severity();

    /** Why the rule exists and what to do about it. */
    String message();

    /** Whether the rule applies to an element with these keys. */
    boolean appliesTo(ElementKeys keys);

    /** The values the rule proposes for keys the element does not write; none by default. */
    default ElementKeys defaults() {
        return ElementKeys.empty();
    }

    /** Checks an element the rule applies to. */
    Outcome check(ElementKeys keys, RealmContext context);
}
