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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.adapter;

import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.MessageBundleApi;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmLocalizationResource;

/** {@link MessageBundleApi} on the Keycloak Admin client. */
public class MessageBundleAdapter extends AbstractAdapter implements MessageBundleApi {

    public MessageBundleAdapter(final Supplier<Keycloak> keycloak) {
        super(keycloak);
    }

    @Override
    public List<String> listLocales(final String realm) {
        return execute(() -> localization(realm).getRealmSpecificLocales());
    }

    @Override
    public Map<String, String> listTexts(final String realm, final String locale) {
        return execute(() -> localization(realm).getRealmLocalizationTexts(locale));
    }

    @Override
    public void saveTexts(final String realm, final String locale, final Map<String, String> texts) {
        execute(() -> localization(realm).createOrUpdateRealmLocalizationTexts(locale, texts));
    }

    @Override
    public void deleteText(final String realm, final String locale, final String key) {
        execute(() -> localization(realm).deleteRealmLocalizationText(locale, key));
    }

    @Override
    public void deleteLocale(final String realm, final String locale) {
        execute(() -> localization(realm).deleteRealmLocalizationTexts(locale));
    }

    private RealmLocalizationResource localization(final String realm) {
        return keycloak().realm(realm).localization();
    }
}
