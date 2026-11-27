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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.connection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.doriangrelu.keycloak.config.application.definition.ClientSecrets;
import io.github.doriangrelu.keycloak.config.application.definition.ExtractedRealm;
import io.github.doriangrelu.keycloak.config.application.definition.RealmNotFoundOnTargetException;
import io.github.doriangrelu.keycloak.config.domain.definition.SecretVariableNames;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import io.github.doriangrelu.keycloak.config.domain.user.model.User;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.client.ClientKeycloakMapper;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.normalization.RealmExportNormalizer;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Reads a realm of a target as an import document (ADR-010): the partial export of the admin API, groups, roles and
 * clients included, its secrets replaced by placeholders, normalized against the reference realm of the version of the
 * target, then the users when asked.
 *
 * <p>No secret value ever reaches the document: the partial export masks them, the masks are replaced by placeholders
 * ({@code {{shopSecret}}}), and any other masked value left is removed. Only {@link #clientSecrets} reads secret values,
 * which it gives in a map that never shows them.
 */
final class RealmExtraction {

    /** The value Keycloak gives a secret in a partial export. */
    static final String MASK = "**********";

    private static final String VAULT = "${vault.";

    private static final String CLIENT_SECRET = "clientSecret";
    private static final String SMTP_PASSWORD = "password";
    private static final String BIND_CREDENTIAL = "bindCredential";

    private static final int USER_PAGE = 100;

    /** The keys of a realm that declare a type of resource: an empty one would declare the type, and clean it up. */
    private static final Set<String> TYPES = Set.of(
            "clients",
            "roles",
            "groups",
            "users",
            "identityProviders",
            "identityProviderMappers",
            "authenticationFlows",
            "authenticatorConfig",
            "components",
            "clientScopes",
            "requiredActions",
            "scopeMappings",
            "clientScopeMappings",
            "clientProfiles",
            "clientPolicies",
            "defaultGroups");

    private final Target target;
    private final String keycloakVersion;
    private final Keycloak keycloak;
    private final RealmExportNormalizer normalizer;
    private final ClientKeycloakMapper clientMapper;
    private final ObjectMapper json;

    RealmExtraction(
            final Target target,
            final String keycloakVersion,
            final Keycloak keycloak,
            final RealmExportNormalizer normalizer,
            final ClientKeycloakMapper clientMapper) {
        this.target = Objects.requireNonNull(target, "target");
        this.keycloakVersion = keycloakVersion;
        this.keycloak = Objects.requireNonNull(keycloak, "keycloak");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.clientMapper = Objects.requireNonNull(clientMapper, "clientMapper");
        this.json = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    ExtractedRealm extract(final String realm, final boolean withUsers) {
        RealmResource resource = existing(realm);
        RealmRepresentation exported = resource.partialExport(true, true);
        Set<String> variables = new HashSet<>();
        confidentialClients(realm, exported.getClients(), variables)
                .forEach((name, client) -> client.setSecret(placeholder(name)));
        identityProviderSecrets(exported.getIdentityProviders(), variables);
        smtpPassword(exported, variables);
        bindCredentials(exported.getComponents(), variables);

        RealmExportNormalizer.Normalized normalized = this.normalizer.normalize(exported, this.keycloakVersion);
        ObjectNode document = this.json.valueToTree(normalized.realm());
        document.remove("id");
        int skippedUsers = 0;
        if (withUsers) {
            Users users = users(resource, realm);
            document.set("users", users.extracted());
            skippedUsers = users.skipped();
        }
        withoutEmptyTypes(document);
        withoutMaskedValues(document);
        List<String> warnings = normalized.missing().stream()
                .map(missing -> "Default " + missing.type().label().toLowerCase(Locale.ROOT) + " '" + missing.key()
                        + "' was deleted in Keycloak: an import may bring it back")
                .toList();
        return new ExtractedRealm(realm, write(document), List.copyOf(variables), warnings, skippedUsers);
    }

    ClientSecrets clientSecrets(final String realm) {
        RealmResource resource = existing(realm);
        RealmRepresentation exported = resource.partialExport(false, true);
        Map<String, String> secrets = new LinkedHashMap<>();
        confidentialClients(realm, exported.getClients(), new HashSet<>())
                .forEach((name, client) -> secrets.put(
                        name, resource.clients().get(client.getId()).getSecret().getValue()));
        return ClientSecrets.of(secrets);
    }

    /** The realm, once checked that the target has it. */
    private RealmResource existing(final String realm) {
        RealmResource resource = this.keycloak.realm(realm);
        try {
            resource.toRepresentation();
        } catch (WebApplicationException failure) {
            if (failure.getResponse() != null && failure.getResponse().getStatus() == 404) {
                throw new RealmNotFoundOnTargetException(realm, this.target);
            }
            throw failure;
        }
        return resource;
    }

    /**
     * The clients whose secret becomes a variable, by the name of the variable, sorted by client id: the confidential
     * clients that are not Keycloak's. The secret of the others is left out: Keycloak owns the secret of its clients,
     * and a public client has none. The clients are named first, so that {@link #clientSecrets} gives the same names.
     */
    private Map<String, ClientRepresentation> confidentialClients(
            final String realm, final List<ClientRepresentation> clients, final Set<String> taken) {
        Map<String, ClientRepresentation> confidential = new LinkedHashMap<>();
        if (clients == null) {
            return confidential;
        }
        List<ClientRepresentation> sorted = clients.stream()
                .filter(client -> client.getClientId() != null)
                .sorted(Comparator.comparing(ClientRepresentation::getClientId))
                .toList();
        for (ClientRepresentation client : sorted) {
            if (!isSecret(client.getSecret())) {
                continue;
            }
            if (Boolean.TRUE.equals(client.isPublicClient())
                    || this.clientMapper.toClient(client).isOwnedByKeycloak(realm)) {
                client.setSecret(null);
                continue;
            }
            String name = SecretVariableNames.unique(SecretVariableNames.client(client.getClientId()), taken);
            taken.add(name);
            confidential.put(name, client);
        }
        return confidential;
    }

    private static void identityProviderSecrets(
            final List<IdentityProviderRepresentation> providers, final Set<String> taken) {
        if (providers == null) {
            return;
        }
        providers.stream()
                .filter(provider -> provider.getAlias() != null && provider.getConfig() != null)
                .filter(provider -> isSecret(provider.getConfig().get(CLIENT_SECRET)))
                .sorted(Comparator.comparing(IdentityProviderRepresentation::getAlias))
                .forEach(provider -> {
                    String name = SecretVariableNames.unique(
                            SecretVariableNames.identityProvider(provider.getAlias()), taken);
                    taken.add(name);
                    Map<String, String> config = new HashMap<>(provider.getConfig());
                    config.put(CLIENT_SECRET, placeholder(name));
                    provider.setConfig(config);
                });
    }

    private static void smtpPassword(final RealmRepresentation exported, final Set<String> taken) {
        Map<String, String> smtp = exported.getSmtpServer();
        if (smtp != null && isSecret(smtp.get(SMTP_PASSWORD))) {
            String name = SecretVariableNames.unique(SecretVariableNames.smtpPassword(), taken);
            taken.add(name);
            Map<String, String> withPlaceholder = new HashMap<>(smtp);
            withPlaceholder.put(SMTP_PASSWORD, placeholder(name));
            exported.setSmtpServer(withPlaceholder);
        }
    }

    /** The bind credentials of the components (LDAP), sub-components included, sorted by component name. */
    private static void bindCredentials(
            final MultivaluedHashMap<String, ComponentExportRepresentation> components, final Set<String> taken) {
        List<ComponentExportRepresentation> all = new ArrayList<>();
        collect(components, all);
        all.stream()
                .filter(component -> component.getName() != null && component.getConfig() != null)
                .filter(component -> isSecret(component.getConfig().getFirst(BIND_CREDENTIAL)))
                .sorted(Comparator.comparing(ComponentExportRepresentation::getName))
                .forEach(component -> {
                    String name = SecretVariableNames.unique(SecretVariableNames.component(component.getName()), taken);
                    taken.add(name);
                    component.getConfig().put(BIND_CREDENTIAL, new ArrayList<>(List.of(placeholder(name))));
                });
    }

    private static void collect(
            final MultivaluedHashMap<String, ComponentExportRepresentation> components,
            final List<ComponentExportRepresentation> all) {
        if (components == null) {
            return;
        }
        components.values().stream()
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .forEach(component -> {
                    all.add(component);
                    collect(component.getSubComponents(), all);
                });
    }

    /** A value that holds a secret: neither empty nor a reference to the vault of Keycloak. */
    private static boolean isSecret(final String value) {
        return value != null && !value.isBlank() && !value.startsWith(VAULT);
    }

    private static String placeholder(final String name) {
        return "{{" + name + "}}";
    }

    /**
     * The local users, sorted by username, without id, date nor credentials; their direct role mappings, without the
     * default roles of the realm, and their groups. The users of a directory and the service accounts are skipped.
     */
    private Users users(final RealmResource resource, final String realm) {
        List<UserRepresentation> local = new ArrayList<>();
        int skipped = 0;
        for (int first = 0; ; first += USER_PAGE) {
            List<UserRepresentation> page = resource.users().list(first, USER_PAGE);
            for (UserRepresentation user : page) {
                if (user.getFederationLink() != null || user.getServiceAccountClientId() != null) {
                    skipped++;
                } else {
                    local.add(user);
                }
            }
            if (page.size() < USER_PAGE) {
                break;
            }
        }
        ArrayNode extracted = this.json.createArrayNode();
        local.stream()
                .sorted(Comparator.comparing(UserRepresentation::getUsername))
                .forEach(user -> extracted.add(userOf(resource.users().get(user.getId()), user, realm)));
        return new Users(extracted, skipped);
    }

    private ObjectNode userOf(final UserResource resource, final UserRepresentation user, final String realm) {
        ObjectNode node = this.json.createObjectNode();
        node.put("username", user.getUsername());
        putIfSet(node, "enabled", user.isEnabled());
        putIfSet(node, "email", user.getEmail());
        putIfSet(node, "emailVerified", user.isEmailVerified());
        putIfSet(node, "firstName", user.getFirstName());
        putIfSet(node, "lastName", user.getLastName());
        if (user.getAttributes() != null && !user.getAttributes().isEmpty()) {
            node.set("attributes", this.json.valueToTree(new TreeMap<>(user.getAttributes())));
        }
        putSorted(node, "requiredActions", user.getRequiredActions());

        MappingsRepresentation mappings = resource.roles().getAll();
        String defaultRoles = User.defaultRolesOf(realm);
        if (mappings.getRealmMappings() != null) {
            putSorted(
                    node,
                    "realmRoles",
                    mappings.getRealmMappings().stream()
                            .map(RoleRepresentation::getName)
                            .filter(name -> !defaultRoles.equals(name))
                            .toList());
        }
        if (mappings.getClientMappings() != null) {
            Map<String, List<String>> clientRoles = new TreeMap<>();
            for (ClientMappingsRepresentation client :
                    mappings.getClientMappings().values()) {
                if (client.getMappings() != null && !client.getMappings().isEmpty()) {
                    clientRoles.put(
                            client.getClient(),
                            client.getMappings().stream()
                                    .map(RoleRepresentation::getName)
                                    .sorted()
                                    .toList());
                }
            }
            if (!clientRoles.isEmpty()) {
                node.set("clientRoles", this.json.valueToTree(clientRoles));
            }
        }
        putSorted(
                node,
                "groups",
                resource.groups(0, Integer.MAX_VALUE).stream()
                        .map(GroupRepresentation::getPath)
                        .toList());
        return node;
    }

    private void putIfSet(final ObjectNode node, final String field, final Object value) {
        if (value != null) {
            node.set(field, this.json.valueToTree(value));
        }
    }

    private static void putSorted(final ObjectNode node, final String field, final Collection<String> values) {
        if (values != null && !values.isEmpty()) {
            ArrayNode array = node.putArray(field);
            new TreeSet<>(values).forEach(array::add);
        }
    }

    /** The types left empty are removed: the files are the only truth, and a type declared empty would be cleaned. */
    private static void withoutEmptyTypes(final ObjectNode document) {
        if (document.get("roles") instanceof ObjectNode roles) {
            removeIfEmpty(roles, "realm");
            removeIfEmpty(roles, "client");
        }
        TYPES.forEach(type -> removeIfEmpty(document, type));
    }

    private static void removeIfEmpty(final ObjectNode node, final String field) {
        JsonNode value = node.get(field);
        if (value != null && (value.isNull() || (value.isContainerNode() && value.isEmpty()))) {
            node.remove(field);
        }
    }

    /** Any value Keycloak masked and no placeholder replaced: the field is left out. */
    private static void withoutMaskedValues(final JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                JsonNode value = fields.next().getValue();
                if (isMasked(value)) {
                    fields.remove();
                } else {
                    withoutMaskedValues(value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(RealmExtraction::withoutMaskedValues);
        }
    }

    private static boolean isMasked(final JsonNode value) {
        if (value.isTextual()) {
            return MASK.equals(value.asText());
        }
        if (value.isArray()) {
            for (JsonNode element : value) {
                if (element.isTextual() && MASK.equals(element.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String write(final ObjectNode document) {
        try {
            return this.json.writeValueAsString(document);
        } catch (JsonProcessingException failure) {
            // the message of Jackson may quote the document: neither it nor the failure is kept
            throw new IllegalStateException("The extracted realm cannot be written as JSON");
        }
    }

    private record Users(ArrayNode extracted, int skipped) {}
}
