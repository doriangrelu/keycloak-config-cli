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

package io.github.doriangrelu.keycloak.config.infrastructure.cli.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.doriangrelu.keycloak.config.AbstractImportIT;
import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.testcontainers.containers.GenericContainer;

/**
 * The first client, role or group of a realm, connected to a real Keycloak: what the confirmation lists is what
 * {@code realm dry-run} shows deleted right after the element is written.
 */
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class FirstDeclarationKeycloakIT {

    private static final GenericContainer<?> KEYCLOAK = AbstractImportIT.KEYCLOAK_CONTAINER;
    private static final String URL = System.getProperty("keycloak.url");
    private static final String ADMIN = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN");
    private static final String ADMIN_PASSWORD = KEYCLOAK.getEnvMap().get("KEYCLOAK_ADMIN_PASSWORD");

    private static final String GROUPS_REALM = "shell-first-groups-it";
    private static final String CLIENTS_REALM = "shell-first-clients-it";
    private static final String ROLES_REALM = "shell-first-roles-it";
    private static final List<String> BUILT_IN_CLIENTS =
            List.of("account", "admin-cli", "broker", "realm-management", "security-admin-console");

    @TempDir
    Path root;

    @BeforeAll
    @AfterAll
    static void removeTheRealms() {
        try (Keycloak admin = admin()) {
            admin.realms().findAll().stream()
                    .filter(realm ->
                            List.of(GROUPS_REALM, CLIENTS_REALM, ROLES_REALM).contains(realm.getRealm()))
                    .forEach(realm -> admin.realm(realm.getRealm()).remove());
        }
    }

    private ApplicationContextRunner shellContexts() throws Exception {
        Files.writeString(
                this.root.resolve("kcc-targets.yaml"),
                "targets:\n  local:\n    url: " + URL + "\n    user: " + ADMIN + "\n",
                StandardCharsets.UTF_8);
        Map<String, String> environment = Map.of("KCC_LOCAL_PASSWORD", ADMIN_PASSWORD);
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
                .withUserConfiguration(
                        CommandLineConfiguration.class,
                        ShellCommandConfiguration.class,
                        ShellAdaptersConfiguration.class)
                .withBean(EnvironmentVariables.class, () -> environment::get)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** What a launch gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    /** Creates the realm folder offline, then runs the lines on it. */
    private Outcome script(final ApplicationContext context, final String realm, final String... lines)
            throws Exception {
        Outcome created = launch(
                context,
                new ShellProperties(
                        this.root,
                        null,
                        "realm create --folder " + realm + " --realm " + realm + " --format yaml",
                        null,
                        null,
                        null));
        assertThat(created.exitCode()).as(created.output()).isZero();
        Path file = this.root.resolve("session.kcc");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return launch(context, new ShellProperties(this.root, null, null, realm, file, null));
    }

    private static Outcome launch(final ApplicationContext context, final ShellProperties properties) {
        StringWriter output = new StringWriter();
        ShellLauncher launcher = new ShellLauncher(
                properties,
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                context.getBean(ShellSession.class),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(ShellRunner.class),
                new PrintWriter(output, true),
                context.getBean(ConnectionCommands.class));
        launcher.run(new DefaultApplicationArguments());
        return new Outcome(launcher.getExitCode(), output.toString());
    }

    /** The names the confirmation listed, read from its information line. */
    private static List<String> listed(final String output, final String cleaned) {
        Matcher line = Pattern.compile("these " + Pattern.quote(cleaned)
                        + " of Keycloak missing from the files will be deleted on local \\((\\d+)\\): (.*)\\.\\R")
                .matcher(output);
        assertThat(line.find()).as(output).isTrue();
        List<String> names = Arrays.asList(line.group(2).split(", "));
        assertThat(names).hasSize(Integer.parseInt(line.group(1)));
        return names;
    }

    /** The keys {@code realm dry-run} shows deleted, for a type label such as {@code Group}. */
    private static List<String> deletedByDryRun(final String output, final String label) {
        Matcher line = Pattern.compile("^  - " + Pattern.quote(label) + " '(.*)'$", Pattern.MULTILINE)
                .matcher(output.substring(output.indexOf("Dry run of ")));
        List<String> keys = new java.util.ArrayList<>();
        while (line.find()) {
            keys.add(line.group(1));
        }
        return keys;
    }

    @Test
    void theListIsTheDryRunDeletions() throws Exception {
        try (Keycloak admin = admin()) {
            RealmResource realm = createRealm(admin, GROUPS_REALM);
            group(realm, "legacy");
            group(realm, "old");
        }
        shellContexts().run(context -> {
            Outcome outcome = script(
                    context,
                    GROUPS_REALM,
                    "connect local",
                    "group create --name team --realm-roles none --client-roles none --declare-type",
                    "realm dry-run");

            assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(10);
            List<String> listed = listed(outcome.output(), "groups");
            assertThat(listed).containsExactlyInAnyOrder("/legacy", "/old");
            assertThat(listed).containsExactlyInAnyOrderElementsOf(deletedByDryRun(outcome.output(), "Group"));
        });
    }

    @Test
    void keycloakOwnedClientsAreNotListed() throws Exception {
        try (Keycloak admin = admin()) {
            RealmResource realm = createRealm(admin, CLIENTS_REALM);
            client(realm, "legacy-app");
        }
        shellContexts().run(context -> {
            Outcome outcome = script(
                    context,
                    CLIENTS_REALM,
                    "connect local",
                    "client create --id shop --type spa --url https://shop.example.org --declare-type",
                    "realm dry-run");

            assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(10);
            List<String> listed = listed(outcome.output(), "clients");
            assertThat(listed).containsExactly("legacy-app").doesNotContainAnyElementsOf(BUILT_IN_CLIENTS);
            assertThat(listed).containsExactlyInAnyOrderElementsOf(deletedByDryRun(outcome.output(), "Client"));
        });
    }

    @Test
    void aClientRoleListsOnlyItsClient() throws Exception {
        try (Keycloak admin = admin()) {
            RealmResource realm = createRealm(admin, ROLES_REALM);
            String shop = client(realm, "shop");
            String other = client(realm, "other");
            realm.clients().get(shop).roles().create(new RoleRepresentation("stale", null, false));
            realm.clients().get(shop).roles().create(new RoleRepresentation("older", null, false));
            realm.clients().get(other).roles().create(new RoleRepresentation("elsewhere", null, false));
            realm.roles().create(new RoleRepresentation("realm-old", null, false));
        }
        shellContexts().run(context -> {
            Outcome outcome = script(
                    context,
                    ROLES_REALM,
                    "client create --id shop --type spa --url https://shop.example.org --declare-type",
                    "client create --id other --type spa --url https://other.example.org",
                    "connect local",
                    "role create --client shop --name admin --description d --declare-type",
                    "realm dry-run");

            assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(10);
            List<String> listed = listed(outcome.output(), "roles of client 'shop'");
            assertThat(listed).containsExactlyInAnyOrder("stale", "older");
            assertThat(listed.stream().map(name -> "shop/" + name).toList())
                    .containsExactlyInAnyOrderElementsOf(deletedByDryRun(outcome.output(), "Client role").stream()
                            .filter(key -> key.startsWith("shop/"))
                            .toList());
            assertThat(outcome.output()).doesNotContain("elsewhere,").doesNotContain("realm-old,");
        });
    }

    private static RealmResource createRealm(final Keycloak admin, final String name) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(name);
        realm.setEnabled(true);
        admin.realms().create(realm);
        return admin.realm(name);
    }

    private static void group(final RealmResource realm, final String name) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(name);
        realm.groups().add(group).close();
    }

    private static String client(final RealmResource realm, final String clientId) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setPublicClient(true);
        return CreatedResponseUtil.getCreatedId(realm.clients().create(client));
    }

    private static Keycloak admin() {
        return KeycloakBuilder.builder()
                .serverUrl(URL)
                .realm("master")
                .clientId("admin-cli")
                .username(ADMIN)
                .password(ADMIN_PASSWORD)
                .build();
    }
}
