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

package io.github.doriangrelu.keycloak.config.application.group.port;

import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import java.util.List;

/** Access to the groups of a realm in Keycloak. */
public interface GroupGateway {

    /** Every group of the realm, parents first, with its role mappings and the names of its children. */
    List<Group> findAll(String realm);

    /** Creates the group under its parent, which must exist, then maps its roles. */
    void create(String realm, Group group);

    /** Updates the group and, when {@code target} specifies them, its role mappings. Children are not touched. */
    void update(String realm, Group current, Group target);

    /** Deletes the group and its children; a group already gone is ignored. */
    void delete(String realm, String path);
}
