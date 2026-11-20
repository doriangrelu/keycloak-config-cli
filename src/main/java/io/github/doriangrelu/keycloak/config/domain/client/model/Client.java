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

package io.github.doriangrelu.keycloak.config.domain.client.model;

import io.github.doriangrelu.keycloak.config.domain.clientscope.model.ProtocolMapper;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import io.github.doriangrelu.keycloak.config.domain.reconcile.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A client of a realm, identified by its client id, or by its name when an import file gives no client id.
 *
 * <p>The fields used by a rule are modeled; the other fields of the Keycloak representation are carried as
 * {@link UnmodeledAttributes} (ADR-002). In a desired client, a {@code null} field is not specified by the import file
 * and keeps its value. Attributes are merged key by key; declared protocol mappers form the exact set of mappers of the
 * client, an existing one being completed by the file; declared default and optional client scopes replace the
 * current ones.
 *
 * @param secret client secret, or {@code null} when not specified
 */
public record Client(
        String clientId,
        String name,
        Map<String, String> attributes,
        Set<String> defaultClientScopes,
        Set<String> optionalClientScopes,
        List<ProtocolMapper> protocolMappers,
        String secret,
        Boolean bearerOnly,
        Boolean publicClient,
        Boolean serviceAccountsEnabled,
        UnmodeledAttributes unmodeled)
        implements Resource<String> {

    /** Clients created by Keycloak in every realm, that an import may update but never create or delete. */
    public static final String REALM_MANAGEMENT = "realm-management";

    public static final String ADMIN_PERMISSIONS = "admin-permissions";

    private static final String NAME_PREFIX = "name:";

    /** Unmodeled field holding the protocol of the client: {@code openid-connect} when absent. */
    static final String PROTOCOL = "protocol";

    public Client {
        if (clientId == null && name == null) {
            throw new InvalidClientException("clients require client id or name.");
        }
        attributes = attributes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        defaultClientScopes = copyOf(defaultClientScopes);
        optionalClientScopes = copyOf(optionalClientScopes);
        // Sorted by name: Keycloak and the import files do not list mappers in the same order
        protocolMappers = protocolMappers == null
                ? null
                : protocolMappers.stream()
                        .sorted(Comparator.comparing(ProtocolMapper::name))
                        .toList();
        unmodeled = unmodeled == null ? UnmodeledAttributes.none() : unmodeled;
    }

    /** The client id, or {@code name:<name>} for a client only known by its name, as in the remote state. */
    @Override
    public String key() {
        return this.clientId != null ? this.clientId : NAME_PREFIX + this.name;
    }

    @Override
    public String stateName() {
        return key();
    }

    /** How the client is called in messages: its name, unless it is the generated name of a Keycloak client. */
    public String identifier() {
        return this.name != null && !hasKeycloakName() ? this.name : this.clientId;
    }

    /** This client once {@code desired} is applied on it. */
    public Client mergedWith(final Client desired) {
        return new Client(
                this.clientId,
                desired.name != null ? desired.name : this.name,
                mergeMap(this.attributes, desired.attributes),
                desired.defaultClientScopes != null ? desired.defaultClientScopes : this.defaultClientScopes,
                desired.optionalClientScopes != null ? desired.optionalClientScopes : this.optionalClientScopes,
                mergeProtocolMappers(desired.protocolMappers),
                desired.secret != null ? desired.secret : this.secret,
                desired.bearerOnly != null ? desired.bearerOnly : this.bearerOnly,
                desired.publicClient != null ? desired.publicClient : this.publicClient,
                desired.serviceAccountsEnabled != null ? desired.serviceAccountsEnabled : this.serviceAccountsEnabled,
                this.unmodeled.overriddenBy(desired.unmodeled));
    }

    /** The same client, known by its client id: for a client an import file only gives by name. */
    public Client withClientId(final String resolvedClientId) {
        return new Client(
                resolvedClientId,
                this.name,
                this.attributes,
                this.defaultClientScopes,
                this.optionalClientScopes,
                this.protocolMappers,
                this.secret,
                this.bearerOnly,
                this.publicClient,
                this.serviceAccountsEnabled,
                this.unmodeled);
    }

    /** The same client without what is updated through dedicated endpoints: protocol mappers and client scopes. */
    public Client withoutMappersAndScopes() {
        return new Client(
                this.clientId,
                this.name,
                this.attributes,
                null,
                null,
                null,
                this.secret,
                this.bearerOnly,
                this.publicClient,
                this.serviceAccountsEnabled,
                this.unmodeled);
    }

    /**
     * This client without what it shares with {@code reference} (normalization): the attributes, client scopes and
     * fields of the reference are left out. The client id, the protocol, the public flag and the protocol mappers are
     * always kept: they tell what the client is.
     */
    public Client withoutValuesOf(final Client reference) {
        Map<String, String> differingAttributes = null;
        if (this.attributes != null) {
            Map<String, String> referenceAttributes = reference.attributes == null ? Map.of() : reference.attributes;
            differingAttributes = new LinkedHashMap<>();
            for (Map.Entry<String, String> attribute : this.attributes.entrySet()) {
                if (attribute.getValue() != null
                        && !attribute.getValue().equals(referenceAttributes.get(attribute.getKey()))) {
                    differingAttributes.put(attribute.getKey(), attribute.getValue());
                }
            }
            differingAttributes = differingAttributes.isEmpty() ? null : differingAttributes;
        }
        return new Client(
                this.clientId,
                differing(this.name, reference.name),
                differingAttributes,
                differing(this.defaultClientScopes, reference.defaultClientScopes),
                differing(this.optionalClientScopes, reference.optionalClientScopes),
                this.protocolMappers,
                differing(this.secret, reference.secret),
                differing(this.bearerOnly, reference.bearerOnly),
                this.publicClient,
                differing(this.serviceAccountsEnabled, reference.serviceAccountsEnabled),
                this.unmodeled.differingFrom(reference.unmodeled, Set.of(), Set.of(PROTOCOL)));
    }

    /**
     * Whether a value of this client differs from the one of {@code reference}. A value the reference has and this
     * client has not is no difference: an import file cannot remove it.
     */
    public boolean hasValuesDifferentFrom(final Client reference) {
        return !withoutValuesOf(reference).equals(reference.withoutValuesOf(reference));
    }

    private static <T> T differing(final T value, final T reference) {
        return value == null || value.equals(reference) ? null : value;
    }

    public boolean isSystemClient() {
        return REALM_MANAGEMENT.equals(this.clientId)
                || ADMIN_PERMISSIONS.equals(this.clientId)
                || ADMIN_PERMISSIONS.equals(this.name);
    }

    /**
     * Clients created by Keycloak, never deleted: those with a generated name ({@code ${client_<clientId>}}), the
     * {@code admin-permissions} client, and the {@code <realm>-realm} clients of the master realm.
     */
    public boolean isOwnedByKeycloak(final String realm) {
        if (hasKeycloakName() || ADMIN_PERMISSIONS.equals(this.clientId)) {
            return true;
        }
        return "master".equals(realm) && this.clientId != null && this.clientId.endsWith("-realm");
    }

    /**
     * Authorization services need a confidential client with a service account
     * (https://github.com/keycloak/keycloak/blob/74695c02423345dab892a0808bf9203c3f92af7c/server-spi-private/src/main/java/org/keycloak/models/utils/RepresentationToModel.java#L2878-L2881).
     * Keycloak's own clients are not checked.
     */
    public void requireSupportForAuthorizationSettings(final String realm) {
        if (REALM_MANAGEMENT.equals(this.clientId) || ADMIN_PERMISSIONS.equals(this.clientId)) {
            return;
        }
        if (Boolean.TRUE.equals(this.bearerOnly) || Boolean.TRUE.equals(this.publicClient)) {
            throw new InvalidClientException(String.format(
                    "Unsupported authorization settings for client '%s' in realm '%s': client must be confidential.",
                    identifier(), realm));
        }
        if (!Boolean.TRUE.equals(this.serviceAccountsEnabled)) {
            throw new InvalidClientException(String.format(
                    "Unsupported authorization settings for client '%s' in realm '%s': serviceAccountsEnabled must be"
                            + " 'true'.",
                    identifier(), realm));
        }
    }

    private boolean hasKeycloakName() {
        if (this.clientId == null || this.name == null) {
            return false;
        }
        return ("${client_" + this.clientId + "}").equals(this.name)
                || ("${client_" + this.clientId.replace('_', '-') + "}").equals(this.name);
    }

    private List<ProtocolMapper> mergeProtocolMappers(final List<ProtocolMapper> desired) {
        if (desired == null) {
            return this.protocolMappers;
        }
        Map<String, ProtocolMapper> current = this.protocolMappers == null
                ? Map.of()
                : this.protocolMappers.stream()
                        .collect(Collectors.toMap(ProtocolMapper::name, Function.identity(), (first, second) -> first));
        return desired.stream()
                .map(wanted -> current.containsKey(wanted.name())
                        ? current.get(wanted.name()).mergedWith(wanted)
                        : wanted)
                .toList();
    }

    private static Map<String, String> mergeMap(final Map<String, String> current, final Map<String, String> desired) {
        if (desired == null) {
            return current;
        }
        Map<String, String> merged = new LinkedHashMap<>(current == null ? Map.of() : current);
        merged.putAll(desired);
        return merged;
    }

    private static Set<String> copyOf(final Set<String> values) {
        return values == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
