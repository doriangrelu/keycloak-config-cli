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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.messagebundle;

import io.github.doriangrelu.keycloak.config.application.messagebundle.port.MessageBundleGateway;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.MessageBundleApi;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link MessageBundleGateway} on the Keycloak Admin API. Localization texts are plain maps: no mapper is needed.
 */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class MessageBundleKeycloakGateway implements MessageBundleGateway {

    private final MessageBundleApi messageBundleApi;

    public MessageBundleKeycloakGateway(final MessageBundleApi messageBundleApi) {
        this.messageBundleApi = messageBundleApi;
    }

    @Override
    public List<MessageBundle> findAll(final String realm) {
        return this.messageBundleApi.listLocales(realm).stream()
                .map(locale -> new MessageBundle(locale, this.messageBundleApi.listTexts(realm, locale)))
                .toList();
    }

    @Override
    public void saveTexts(final String realm, final String locale, final Map<String, String> texts) {
        this.messageBundleApi.saveTexts(realm, locale, texts);
    }

    @Override
    public void deleteTexts(final String realm, final String locale, final Set<String> keys) {
        keys.forEach(key -> this.messageBundleApi.deleteText(realm, locale, key));
    }

    @Override
    public void deleteLocale(final String realm, final String locale) {
        this.messageBundleApi.deleteLocale(realm, locale);
    }
}
