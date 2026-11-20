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

package io.github.doriangrelu.keycloak.config.application.messagebundle;

import io.github.doriangrelu.keycloak.config.application.messagebundle.port.MessageBundleGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.messagebundle.model.MessageBundle;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Message bundles (localization texts), managed by {@code import.managed.message-bundles}. In {@code full} mode, the
 * keys missing from a declared locale are removed. A whole locale is removed only when the remote state shows that
 * keycloak-config-cli created it.
 */
public final class MessageBundleReconciler implements Reconciler<String, MessageBundle> {

    /** Remote state entry of the locales created by keycloak-config-cli. */
    public static final String STATE_SCOPE = "message-bundles";

    private final MessageBundleGateway messageBundles;

    public MessageBundleReconciler(final MessageBundleGateway messageBundles) {
        this.messageBundles = Objects.requireNonNull(messageBundles, "messageBundles");
    }

    @Override
    public ResourceType type() {
        return ResourceType.MESSAGE_BUNDLE;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of();
    }

    @Override
    public List<MessageBundle> read(final RealmContext context, final Collection<MessageBundle> desired) {
        return this.messageBundles.findAll(context.realm());
    }

    @Override
    public MessageBundle merge(final RealmContext context, final MessageBundle current, final MessageBundle desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final MessageBundle resource) {
        this.messageBundles.saveTexts(context.realm(), resource.locale(), resource.texts());
    }

    @Override
    public void update(final RealmContext context, final MessageBundle current, final MessageBundle target) {
        Map<String, String> changed = current.textsChangedIn(target);
        if (!changed.isEmpty()) {
            this.messageBundles.saveTexts(context.realm(), target.locale(), changed);
        }
        Set<String> removed = current.keysRemovedIn(target);
        if (!removed.isEmpty()) {
            this.messageBundles.deleteTexts(context.realm(), target.locale(), removed);
        }
    }

    @Override
    public void delete(final RealmContext context, final MessageBundle resource) {
        this.messageBundles.deleteLocale(context.realm(), resource.locale());
    }

    @Override
    public String stateScope(final MessageBundle resource) {
        return STATE_SCOPE;
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final MessageBundle resource) {
        return false;
    }

    @Override
    public boolean prunesWithoutRemoteState() {
        return false;
    }
}
