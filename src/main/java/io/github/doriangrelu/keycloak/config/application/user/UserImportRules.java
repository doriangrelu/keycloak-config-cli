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

package io.github.doriangrelu.keycloak.config.application.user;

/**
 * Behaviors of the user import.
 *
 * @param removeDefaultRole whether the default roles of the realm are removed from a user that does not declare them
 * @param skipAttributesForFederatedUser whether the attributes of a user linked to a user federation are left as they
 *     are
 */
public record UserImportRules(boolean removeDefaultRole, boolean skipAttributesForFederatedUser) {}
