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

package io.github.doriangrelu.keycloak.config.application.reconcile;

import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Objects;

/**
 * A value the files declare that Keycloak hides (it answers a mask): it is not compared, so it never counts as a
 * change; it is still sent when its element is updated for another reason. Never holds the value.
 *
 * @param field the field, such as {@code smtpServer.password} or {@code config.clientSecret}
 */
public record NotComparedValue(ResourceType type, Object key, String field) {

    public NotComparedValue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(field, "field");
    }
}
