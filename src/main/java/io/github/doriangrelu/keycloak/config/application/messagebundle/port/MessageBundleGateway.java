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

package io.github.doriangrelu.keycloak.config.application.messagebundle.port;

import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Access to the localization texts of a realm in Keycloak. */
public interface MessageBundleGateway {

    /** Texts of every locale that has texts specific to the realm. */
    List<MessageBundle> findAll(String realm);

    /** Creates or updates the given texts of a locale; other texts of the locale are kept. */
    void saveTexts(String realm, String locale, Map<String, String> texts);

    void deleteTexts(String realm, String locale, Set<String> keys);

    void deleteLocale(String realm, String locale);
}
