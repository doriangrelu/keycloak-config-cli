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

import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ProtectionRule;
import java.util.List;

/**
 * One realm, as an import file declares it. A file may declare several realms, and several files the same realm: the
 * import merges them realm by realm.
 */
public interface RealmDeclaration {

    String realm();

    /** The file the realm comes from, for the messages. */
    String source();

    /** The resources this file protects from deletion. */
    List<ProtectionRule> protections();

    /** The realm settings alone: they can be computed before the realm exists, and create it. */
    DesiredState settings();

    /**
     * Everything the file declares for the realm.
     *
     * @param realmExists whether the realm exists in Keycloak: some declarations are completed from it, such as a
     *     client given by name only
     */
    DesiredState desiredState(boolean realmExists);

    /** Whether the file declares a user storage, such as an LDAP, whose users may be synchronized after the import. */
    boolean declaresUserStorage();
}
