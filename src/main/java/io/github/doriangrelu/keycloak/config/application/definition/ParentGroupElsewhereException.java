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

import io.github.doriangrelu.keycloak.config.application.common.ApplicationException;

/** The parent of a new subgroup is written in another file than the one the groups are written to. */
public final class ParentGroupElsewhereException extends ApplicationException {

    public ParentGroupElsewhereException(final String parent, final String parentFile, final String groupsFile) {
        super("Group '" + parent + "' is written in " + parentFile + ", not in " + groupsFile
                + " where the shell writes groups: put the groups in one file, or edit the files by hand");
    }
}
