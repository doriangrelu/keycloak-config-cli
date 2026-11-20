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

package io.github.doriangrelu.keycloak.config.application.normalization;

import io.github.doriangrelu.keycloak.config.application.normalization.NormalizedRealm.MissingResource;
import io.github.doriangrelu.keycloak.config.application.normalization.port.ReferenceRealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.DesiredState;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticationFlow;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.AuthenticatorConfig;
import io.github.doriangrelu.keycloak.config.domain.authenticationflow.model.ClientFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.client.model.Client;
import io.github.doriangrelu.keycloak.config.domain.clientauthorization.model.ClientAuthorization;
import io.github.doriangrelu.keycloak.config.domain.component.model.Component;
import io.github.doriangrelu.keycloak.config.domain.group.model.Group;
import io.github.doriangrelu.keycloak.config.domain.normalization.ReferenceComparison;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmSettings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import io.github.doriangrelu.keycloak.config.domain.role.model.Role;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleComposites;
import io.github.doriangrelu.keycloak.config.domain.role.model.RoleKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Normalizes an export: keeps what differs from the reference realm of its Keycloak version, so that the import file
 * only holds what was configured.
 *
 * <ul>
 *   <li>Realm settings, flow bindings of the realm and clients keep only their differing values. A client absent from
 *       the reference is compared with a client created with default values.
 *   <li>A resource of another type is kept whole when it differs.
 *   <li>A resource made of others is kept with its parts: a client with its flow bindings and authorization, a role
 *       with its composites, a group with its whole tree, a component with all its sub-components, a flow with the
 *       authenticator configs it uses.
 * </ul>
 */
public class RealmNormalization {

    /** Types compared as a whole: kept when they differ. */
    private static final List<ResourceType> COMPARED_AS_A_WHOLE = List.of(
            ResourceType.DEFAULT_CLIENT_SCOPE,
            ResourceType.DEFAULT_GROUP,
            ResourceType.CLIENT_SCOPE,
            ResourceType.CLIENT_PROFILE,
            ResourceType.CLIENT_POLICY,
            ResourceType.SCOPE_MAPPING,
            ResourceType.CLIENT_SCOPE_MAPPING,
            ResourceType.IDENTITY_PROVIDER,
            ResourceType.IDENTITY_PROVIDER_MAPPER,
            ResourceType.REQUIRED_ACTION);

    private final ReferenceRealmGateway referenceRealms;

    public RealmNormalization(final ReferenceRealmGateway referenceRealms) {
        this.referenceRealms = referenceRealms;
    }

    /**
     * @param exported resources of the exported realm, without their Keycloak ids
     */
    public NormalizedRealm normalize(final String realm, final String keycloakVersion, final DesiredState exported) {
        DesiredState reference = this.referenceRealms.referenceRealm(keycloakVersion, realm);
        Normalization normalization = new Normalization(exported, reference);

        normalization.realmSettings();
        normalization.realmFlowBindings();
        COMPARED_AS_A_WHOLE.forEach(normalization::asAWhole);
        normalization.clients(clientId -> this.referenceRealms.referenceClient(keycloakVersion, clientId));
        normalization.roles();
        normalization.groups();
        normalization.flows();
        normalization.components();
        return new NormalizedRealm(normalization.normalized, normalization.missing);
    }

    /** The normalization of one export. */
    private static final class Normalization {

        private final DesiredState exported;
        private final DesiredState reference;
        private final DesiredState normalized = new DesiredState();
        private final List<MissingResource> missing = new ArrayList<>();

        private Normalization(final DesiredState exported, final DesiredState reference) {
            this.exported = exported;
            this.reference = reference;
        }

        void realmSettings() {
            List<RealmSettings> settings = of(this.exported, ResourceType.REALM_SETTINGS);
            List<RealmSettings> referenceSettings = of(this.reference, ResourceType.REALM_SETTINGS);
            settings.stream()
                    .findFirst()
                    .map(declared -> referenceSettings.stream()
                            .findFirst()
                            .map(declared::withoutValuesOf)
                            .orElse(declared))
                    .ifPresent(kept -> keep(ResourceType.REALM_SETTINGS, List.of(kept)));
        }

        void realmFlowBindings() {
            List<RealmFlowBindings> bindings = of(this.exported, ResourceType.REALM_FLOW_BINDING);
            List<RealmFlowBindings> referenceBindings = of(this.reference, ResourceType.REALM_FLOW_BINDING);
            bindings.stream()
                    .findFirst()
                    .map(declared -> referenceBindings.stream()
                            .findFirst()
                            .map(declared::withoutValuesOf)
                            .orElse(declared))
                    .filter(kept -> !kept.isEmpty())
                    .ifPresent(kept -> keep(ResourceType.REALM_FLOW_BINDING, List.of(kept)));
        }

        <K, R extends Resource<K>> void asAWhole(final ResourceType type) {
            ReferenceComparison<K, R> comparison = compare(type, Object::equals);
            keep(type, comparison.differing());
        }

        void clients(final Function<String, Client> referenceClient) {
            Map<String, ClientFlowBindings> bindings = byKey(this.exported, ResourceType.CLIENT_FLOW_BINDING);
            Map<String, ClientFlowBindings> referenceBindings = byKey(this.reference, ResourceType.CLIENT_FLOW_BINDING);
            Map<String, ClientAuthorization> authorizations = byKey(this.exported, ResourceType.CLIENT_AUTHORIZATION);
            Map<String, ClientAuthorization> referenceAuthorizations =
                    byKey(this.reference, ResourceType.CLIENT_AUTHORIZATION);
            Map<String, Client> referenceClients = byKey(this.reference, ResourceType.CLIENT);

            ReferenceComparison<String, Client> comparison = compare(
                    ResourceType.CLIENT,
                    (Client client, Client referenceOne) -> !client.hasValuesDifferentFrom(referenceOne)
                            && Objects.equals(bindings.get(client.key()), referenceBindings.get(client.key()))
                            && Objects.equals(
                                    authorizations.get(client.key()), referenceAuthorizations.get(client.key())));
            List<Client> clients = new ArrayList<>();
            List<ClientFlowBindings> keptBindings = new ArrayList<>();
            List<ClientAuthorization> keptAuthorizations = new ArrayList<>();
            for (Client client : comparison.differing()) {
                Client referenceOne = referenceClients.get(client.key());
                if (referenceOne == null && client.clientId() != null) {
                    referenceOne = referenceClient.apply(client.clientId());
                }
                clients.add(referenceOne == null ? client : client.withoutValuesOf(referenceOne));
                Optional.ofNullable(bindings.get(client.key())).ifPresent(keptBindings::add);
                Optional.ofNullable(authorizations.get(client.key())).ifPresent(keptAuthorizations::add);
            }
            keep(ResourceType.CLIENT, clients);
            keep(ResourceType.CLIENT_FLOW_BINDING, keptBindings);
            keep(ResourceType.CLIENT_AUTHORIZATION, keptAuthorizations);
        }

        void roles() {
            Map<RoleKey, RoleComposites> composites = byKey(this.exported, ResourceType.ROLE_COMPOSITE);
            Map<RoleKey, RoleComposites> referenceComposites = byKey(this.reference, ResourceType.ROLE_COMPOSITE);
            List<RoleComposites> keptComposites = new ArrayList<>();
            for (ResourceType type : List.of(ResourceType.REALM_ROLE, ResourceType.CLIENT_ROLE)) {
                ReferenceComparison<RoleKey, Role> comparison = compare(
                        type,
                        (Role role, Role referenceOne) -> role.equals(referenceOne)
                                && Objects.equals(composites.get(role.key()), referenceComposites.get(role.key())));
                keep(type, comparison.differing());
                comparison.differing().stream()
                        .map(role -> composites.get(role.key()))
                        .filter(Objects::nonNull)
                        .forEach(keptComposites::add);
            }
            keep(ResourceType.ROLE_COMPOSITE, keptComposites);
        }

        /** A group that differs is kept with its whole tree, from its top-level group. */
        void groups() {
            ReferenceComparison<String, Group> comparison = compare(ResourceType.GROUP, Object::equals);
            Set<String> roots = comparison.differing().stream()
                    .map(group -> rootOf(group.path()))
                    .collect(Collectors.toSet());
            List<Group> groups = of(this.exported, ResourceType.GROUP);
            keep(
                    ResourceType.GROUP,
                    groups.stream()
                            .filter(group -> roots.contains(rootOf(group.path())))
                            .toList());
        }

        /** A flow that differs is kept with the authenticator configs it uses. */
        void flows() {
            ReferenceComparison<String, AuthenticationFlow> comparison =
                    compare(ResourceType.AUTHENTICATION_FLOW, AuthenticationFlow::hasSameStructureAs);
            keep(ResourceType.AUTHENTICATION_FLOW, comparison.differing());

            Set<String> used = new HashSet<>();
            comparison
                    .differing()
                    .forEach(flow -> used.addAll(flow.authenticatorConfigs().keySet()));
            Map<String, AuthenticatorConfig> referenceConfigs =
                    byKey(this.reference, ResourceType.AUTHENTICATOR_CONFIG);
            Set<String> inReference = referenceConfigs.keySet();
            ReferenceComparison<String, AuthenticatorConfig> configs =
                    compare(ResourceType.AUTHENTICATOR_CONFIG, Object::equals);
            Set<String> changed = configs.differing().stream()
                    .map(AuthenticatorConfig::alias)
                    .filter(inReference::contains)
                    .collect(Collectors.toSet());
            List<AuthenticatorConfig> exportedConfigs = of(this.exported, ResourceType.AUTHENTICATOR_CONFIG);
            keep(
                    ResourceType.AUTHENTICATOR_CONFIG,
                    exportedConfigs.stream()
                            .filter(config -> used.contains(config.alias()) || changed.contains(config.alias()))
                            .toList());
        }

        /** A component that differs is kept with its whole tree, from its top-level component. */
        void components() {
            List<Component> components = new ArrayList<>(of(this.exported, ResourceType.COMPONENT));
            components.addAll(of(this.exported, ResourceType.SUB_COMPONENT));
            List<Component> referenceComponents = new ArrayList<>(of(this.reference, ResourceType.COMPONENT));
            referenceComponents.addAll(of(this.reference, ResourceType.SUB_COMPONENT));
            ReferenceComparison<Component.Key, Component> comparison =
                    ReferenceComparison.of(components, referenceComponents, Component::hasSameContentAs);
            comparison.missing().forEach(key -> missing(ResourceType.COMPONENT, key));
            Set<Component.Key> roots = comparison.differing().stream()
                    .map(component -> rootOf(component.key()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            keep(
                    ResourceType.COMPONENT,
                    components.stream()
                            .filter(component -> component.key().isTopLevel())
                            .filter(component -> roots.contains(component.key()))
                            .toList());
            keep(
                    ResourceType.SUB_COMPONENT,
                    components.stream()
                            .filter(component -> !component.key().isTopLevel())
                            .filter(component -> roots.contains(rootOf(component.key())))
                            .toList());
        }

        private <K, R extends Resource<K>> ReferenceComparison<K, R> compare(
                final ResourceType type, final java.util.function.BiPredicate<R, R> same) {
            List<R> exportedOnes = of(this.exported, type);
            List<R> referenceOnes = of(this.reference, type);
            ReferenceComparison<K, R> comparison = ReferenceComparison.of(exportedOnes, referenceOnes, same);
            comparison.missing().forEach(key -> missing(type, key));
            return comparison;
        }

        private void missing(final ResourceType type, final Object key) {
            this.missing.add(new MissingResource(type, String.valueOf(key)));
        }

        private void keep(final ResourceType type, final List<? extends Resource<?>> kept) {
            if (!kept.isEmpty()) {
                this.normalized.declare(type, kept);
            }
        }

        @SuppressWarnings("unchecked")
        private static <R> List<R> of(final DesiredState state, final ResourceType type) {
            return state.resources(type).map(resources -> (List<R>) resources).orElse(List.of());
        }

        private static <K, R extends Resource<K>> Map<K, R> byKey(final DesiredState state, final ResourceType type) {
            List<R> resources = of(state, type);
            return resources.stream()
                    .collect(Collectors.toMap(Resource::key, Function.identity(), (first, second) -> first));
        }

        private static String rootOf(final String path) {
            int separator = path.indexOf('/', 1);
            return separator < 0 ? path : path.substring(0, separator);
        }

        private static Component.Key rootOf(final Component.Key key) {
            Component.Key root = key;
            while (!root.isTopLevel()) {
                root = root.parent();
            }
            return root;
        }
    }
}
