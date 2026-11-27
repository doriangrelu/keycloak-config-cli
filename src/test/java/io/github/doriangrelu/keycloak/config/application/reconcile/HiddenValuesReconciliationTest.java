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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.doriangrelu.keycloak.config.application.component.ComponentReconciler;
import io.github.doriangrelu.keycloak.config.application.component.port.ComponentGateway;
import io.github.doriangrelu.keycloak.config.application.identityprovider.IdentityProviderReconciler;
import io.github.doriangrelu.keycloak.config.application.identityprovider.port.IdentityProviderGateway;
import io.github.doriangrelu.keycloak.config.application.realm.RealmSettingsReconciler;
import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.domain.common.HiddenValues;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.identityprovider.model.IdentityProvider;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The values Keycloak hides behind its mask: never a change on their own, noted as not compared, and still sent when
 * the element is updated for another reason.
 */
class HiddenValuesReconciliationTest {

    private static final String MASK = HiddenValues.MASK;
    private static final String REALM = "realm";

    private final RealmGateway realms = mock(RealmGateway.class);
    private final IdentityProviderGateway identityProviders = mock(IdentityProviderGateway.class);
    private final ComponentGateway components = mock(ComponentGateway.class);

    private final ReconciliationEngine engine = new ReconciliationEngine(List.of(
            new RealmSettingsReconciler(this.realms, "obsolete."),
            new IdentityProviderReconciler(this.identityProviders),
            ComponentReconciler.ofComponents(this.components)));

    private static RealmSettings settings(final String displayName, final String password) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("displayName", displayName);
        values.put("smtpServer", Map.of("host", "smtp.example.org", "password", password));
        return new RealmSettings(REALM, new UnmodeledAttributes(values));
    }

    private static IdentityProvider google(final String clientId, final String secret) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("providerId", "google");
        values.put("config", Map.of("clientId", clientId, "clientSecret", secret));
        return new IdentityProvider(
                "google", IdentityProvider.DEFAULT_FIRST_BROKER_LOGIN_FLOW, null, new UnmodeledAttributes(values));
    }

    private static Component ldap(final String url, final String credential) {
        return new Component(
                new Component.Key(null, "org.keycloak.storage.UserStorageProvider", null, "ldap"),
                null,
                "ldap",
                Map.of("connectionUrl", List.of(url), "bindCredential", List.of(credential)));
    }

    private RealmContext apply(final ResourceType type, final Resource<?> desired) {
        RealmContext context = new RealmContext(REALM, false);
        this.engine.apply(context, new DesiredState().declare(type, List.of(desired)));
        return context;
    }

    @Test
    void aHiddenSmtpPasswordAloneIsNoChange() {
        when(this.realms.findSettings(REALM)).thenReturn(Optional.of(settings("Shop", MASK)));

        RealmContext context = apply(ResourceType.REALM_SETTINGS, settings("Shop", "s3cret"));

        assertThat(context.plan()).isEmpty();
        assertThat(context.notCompared())
                .containsExactly(new NotComparedValue(ResourceType.REALM_SETTINGS, REALM, "smtpServer.password"));
        verify(this.realms, never()).update(any());
    }

    @Test
    void anUpdateForAnotherReasonSendsTheValueOfTheFilesAndDoesNotShowIt() {
        when(this.realms.findSettings(REALM)).thenReturn(Optional.of(settings("Shop", MASK)));

        RealmContext context = apply(ResourceType.REALM_SETTINGS, settings("New shop", "s3cret"));

        assertThat(context.plan()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(PlannedChange.Kind.UPDATE);
            assertThat(String.valueOf(change.after())).doesNotContain("s3cret");
        });
        ArgumentCaptor<RealmSettings> sent = ArgumentCaptor.forClass(RealmSettings.class);
        verify(this.realms).update(sent.capture());
        assertThat(sent.getValue().settings().values().get("smtpServer"))
                .isEqualTo(Map.of("host", "smtp.example.org", "password", "s3cret"));
    }

    @Test
    void aHiddenIdentityProviderSecretAloneIsNoChange() {
        when(this.identityProviders.findAll(REALM)).thenReturn(List.of(google("google-client", MASK)));

        RealmContext context = apply(ResourceType.IDENTITY_PROVIDER, google("google-client", "s3cret"));

        assertThat(context.plan()).isEmpty();
        assertThat(context.notCompared())
                .containsExactly(new NotComparedValue(ResourceType.IDENTITY_PROVIDER, "google", "config.clientSecret"));
        verify(this.identityProviders, never()).update(anyString(), any());
    }

    @Test
    void anIdentityProviderUpdatedForAnotherReasonSendsItsSecret() {
        when(this.identityProviders.findAll(REALM)).thenReturn(List.of(google("google-client", MASK)));

        RealmContext context = apply(ResourceType.IDENTITY_PROVIDER, google("other-client", "s3cret"));

        assertThat(context.plan()).extracting(PlannedChange::kind).containsExactly(PlannedChange.Kind.UPDATE);
        ArgumentCaptor<IdentityProvider> sent = ArgumentCaptor.forClass(IdentityProvider.class);
        verify(this.identityProviders).update(anyString(), sent.capture());
        assertThat(sent.getValue().unmodeled().values().get("config"))
                .isEqualTo(Map.of("clientId", "other-client", "clientSecret", "s3cret"));
    }

    @Test
    void aHiddenBindCredentialAloneIsNoChange() {
        when(this.components.findAll(REALM)).thenReturn(List.of(ldap("ldap://ldap.example.org", MASK)));

        RealmContext context = apply(ResourceType.COMPONENT, ldap("ldap://ldap.example.org", "s3cret"));

        assertThat(context.plan()).isEmpty();
        assertThat(context.notCompared()).extracting(NotComparedValue::field).containsExactly("config.bindCredential");
        verify(this.components, never()).update(anyString(), any());
    }

    @Test
    void aComponentUpdatedForAnotherReasonSendsItsCredential() {
        when(this.components.findAll(REALM)).thenReturn(List.of(ldap("ldap://ldap.example.org", MASK)));

        RealmContext context = apply(ResourceType.COMPONENT, ldap("ldap://other.example.org", "s3cret"));

        assertThat(context.plan()).extracting(PlannedChange::kind).containsExactly(PlannedChange.Kind.UPDATE);
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(this.components).update(anyString(), sent.capture());
        assertThat(sent.getValue().config().get("bindCredential")).containsExactly("s3cret");
    }

    @Test
    void aHiddenValueTheFilesDoNotDeclareIsNotNoted() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("displayName", "Shop");
        values.put("smtpServer", Map.of("host", "smtp.example.org"));
        when(this.realms.findSettings(REALM)).thenReturn(Optional.of(settings("Shop", MASK)));

        RealmContext context =
                apply(ResourceType.REALM_SETTINGS, new RealmSettings(REALM, new UnmodeledAttributes(values)));

        assertThat(context.notCompared()).isEmpty();
    }

    @Test
    void aValueKeycloakShowsIsComparedAsBefore() {
        when(this.realms.findSettings(REALM)).thenReturn(Optional.of(settings("Shop", "old")));

        RealmContext context = apply(ResourceType.REALM_SETTINGS, settings("Shop", "new"));

        assertThat(context.plan()).extracting(PlannedChange::kind).containsExactly(PlannedChange.Kind.UPDATE);
        assertThat(context.notCompared()).isEmpty();
    }
}
