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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.definition.ConnectTarget;
import io.github.doriangrelu.keycloak.config.application.definition.Connection;
import io.github.doriangrelu.keycloak.config.application.definition.KeycloakUnreachableException;
import io.github.doriangrelu.keycloak.config.application.definition.MissingAnswerException;
import io.github.doriangrelu.keycloak.config.application.definition.UnknownTargetException;
import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.definition.port.Questions;
import io.github.doriangrelu.keycloak.config.application.definition.port.Targets;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.shell.core.InputReader;

/** The commands that connect, disconnect and list the targets, on doubles of the targets and of Keycloak. */
class ConnectionCommandsTest {

    private static final Target LOCAL = target("local", GrantType.PASSWORD);
    private static final Target DEV = target("dev-eu", GrantType.CLIENT_CREDENTIALS);

    private final List<String> events = new ArrayList<>();
    private final Map<String, String> environment = new HashMap<>();
    private final List<String> askedPrompts = new ArrayList<>();
    private final List<String> typed = new ArrayList<>();
    private final Map<String, String> secretsSeen = new HashMap<>();
    private final List<String> unreachable = new ArrayList<>();
    private final ShellSession session = new ShellSession();
    private final StringWriter output = new StringWriter();

    private final KeycloakConnector connector = (target, secret) -> {
        if (this.unreachable.contains(target.name())) {
            throw new KeycloakUnreachableException(target, "Connection refused", null);
        }
        this.events.add("open " + target.name());
        this.secretsSeen.put(target.name(), secret);
        return new RecordingConnection(target, this.events);
    };

    private final ConnectionCommands commands = new ConnectionCommands(
            new ConnectTarget(
                    new Targets() {
                        @Override
                        public List<Target> all() {
                            return List.of(LOCAL, DEV);
                        }

                        @Override
                        public Optional<Target> find(final String name) {
                            return all().stream()
                                    .filter(target -> target.name().equals(name))
                                    .findFirst();
                        }
                    },
                    this.connector),
            this.session,
            this.environment::get);

    private static Target target(final String name, final GrantType grantType) {
        return new Target(
                name,
                "http://" + name + ":8080",
                "master",
                grantType == GrantType.PASSWORD ? "admin-cli" : "kcc-dev",
                grantType,
                grantType == GrantType.PASSWORD ? Optional.of("admin") : Optional.empty(),
                true,
                Duration.ofSeconds(10));
    }

    private Questions questions() {
        InputReader reader = new InputReader() {
            @Override
            public char[] readPassword(final String prompt) {
                ConnectionCommandsTest.this.askedPrompts.add(prompt);
                return ConnectionCommandsTest.this.typed.isEmpty()
                        ? null
                        : ConnectionCommandsTest.this.typed.removeFirst().toCharArray();
            }
        };
        return new ConsoleQuestions(reader, new PrintWriter(this.output, true));
    }

    @Test
    void theEnvironmentPasswordIsUsedWithoutAskingAnything() {
        this.environment.put("KCC_LOCAL_PASSWORD", "from-env");

        String result = this.commands.connect("local", questions());

        assertThat(result).isEqualTo("Connected to local (http://local:8080, Keycloak 26.5.3)");
        assertThat(this.secretsSeen).containsEntry("local", "from-env");
        assertThat(this.askedPrompts).isEmpty();
        assertThat(this.session.connection()).isPresent();
    }

    @Test
    void theEnvironmentSecretOfAClientIsReadFromTheSecretVariable() {
        this.environment.put("KCC_DEV_EU_SECRET", "client-secret");
        this.environment.put("KCC_DEV_EU_PASSWORD", "not-this-one");

        this.commands.connect("dev-eu", questions());

        assertThat(this.secretsSeen).containsEntry("dev-eu", "client-secret");
    }

    @Test
    void aMissingPasswordIsAskedForTheUser() {
        this.typed.add("typed-password");

        this.commands.connect("local", questions());

        assertThat(this.secretsSeen).containsEntry("local", "typed-password");
        assertThat(this.askedPrompts).singleElement().asString().contains("Password of 'admin'", "KCC_LOCAL_PASSWORD");
        assertThat(this.output.toString()).doesNotContain("typed-password");
    }

    @Test
    void aMissingSecretIsAskedForTheClient() {
        this.typed.add("typed-secret");

        this.commands.connect("dev-eu", questions());

        assertThat(this.askedPrompts)
                .singleElement()
                .asString()
                .contains("Secret of client 'kcc-dev'", "KCC_DEV_EU_SECRET");
    }

    @Test
    void aBlankVariableCountsAsMissing() {
        this.environment.put("KCC_LOCAL_PASSWORD", "  ");
        this.typed.add("typed-password");

        this.commands.connect("local", questions());

        assertThat(this.secretsSeen).containsEntry("local", "typed-password");
    }

    @Test
    void inScriptModeAMissingSecretNamesTheVariableToSet() {
        assertThatThrownBy(() -> this.commands.connect("local", questions()))
                .isInstanceOf(MissingAnswerException.class)
                .hasMessageContaining("KCC_LOCAL_PASSWORD");
        assertThat(this.session.connection()).isEmpty();
    }

    @Test
    void anUnknownTargetListsTheKnownOnes() {
        assertThatThrownBy(() -> this.commands.connect("prod", questions()))
                .isInstanceOf(UnknownTargetException.class)
                .hasMessageContaining("local, dev-eu");
    }

    @Test
    void switchingTargetsClosesThePreviousConnectionAfterTheNewOneIsOpen() {
        this.environment.put("KCC_LOCAL_PASSWORD", "a");
        this.environment.put("KCC_DEV_EU_SECRET", "b");

        this.commands.connect("local", questions());
        this.commands.connect("dev-eu", questions());

        assertThat(this.events).containsExactly("open local", "open dev-eu", "close local");
        assertThat(this.session.connection()).map(c -> c.target().name()).contains("dev-eu");
    }

    @Test
    void aFailedSwitchKeepsThePreviousConnection() {
        this.environment.put("KCC_LOCAL_PASSWORD", "a");
        this.environment.put("KCC_DEV_EU_SECRET", "b");
        this.commands.connect("local", questions());
        this.unreachable.add("dev-eu");

        assertThatThrownBy(() -> this.commands.connect("dev-eu", questions()))
                .isInstanceOf(KeycloakUnreachableException.class);

        assertThat(this.events).containsExactly("open local");
        assertThat(this.session.connection()).map(c -> c.target().name()).contains("local");
    }

    @Test
    void disconnectWithoutConnectionSaysSo() {
        assertThat(this.commands.disconnect()).isEqualTo("Not connected: use 'connect <target>'");
    }

    @Test
    void disconnectClosesTheConnection() {
        this.environment.put("KCC_LOCAL_PASSWORD", "a");
        this.commands.connect("local", questions());

        assertThat(this.commands.disconnect()).isEqualTo("Disconnected from local");

        assertThat(this.events).containsExactly("open local", "close local");
        assertThat(this.session.connection()).isEmpty();
    }

    @Test
    void targetListMarksTheConnectedOne() {
        this.environment.put("KCC_DEV_EU_SECRET", "b");
        assertThat(this.commands.list())
                .isEqualTo("local  http://local:8080  password" + System.lineSeparator()
                        + "dev-eu  http://dev-eu:8080  client_credentials");

        this.commands.connect("dev-eu", questions());

        assertThat(this.commands.list())
                .isEqualTo("local  http://local:8080  password" + System.lineSeparator()
                        + "dev-eu  http://dev-eu:8080  client_credentials  (connected)");
    }

    @Test
    void closingTheSessionClosesTheConnection() {
        this.environment.put("KCC_LOCAL_PASSWORD", "a");
        this.commands.connect("local", questions());

        this.session.close();
        this.session.close();

        assertThat(this.events).containsExactly("open local", "close local");
        assertThat(this.session.connection()).isEmpty();
    }

    @Test
    void thePromptShowsTheRealmAndTheTarget() {
        ShellPrompt prompt = new ShellPrompt(this.session);
        assertThat(prompt.text()).isEqualTo("kcc>");

        this.session.open("shop");
        assertThat(prompt.text()).isEqualTo("kcc:shop>");

        this.environment.put("KCC_LOCAL_PASSWORD", "a");
        this.commands.connect("local", questions());
        assertThat(prompt.text()).isEqualTo("kcc:shop@local>");

        this.commands.disconnect();
        assertThat(prompt.text()).isEqualTo("kcc:shop>");
    }

    @Test
    void thePromptShowsTheTargetAlone() {
        this.environment.put("KCC_LOCAL_PASSWORD", "a");
        this.commands.connect("local", questions());

        assertThat(new ShellPrompt(this.session).text()).isEqualTo("kcc@local>");
    }

    private static final class RecordingConnection implements Connection {

        private final Target target;
        private final List<String> events;
        private boolean closed;

        RecordingConnection(final Target target, final List<String> events) {
            this.target = target;
            this.events = events;
        }

        @Override
        public Target target() {
            return this.target;
        }

        @Override
        public String keycloakVersion() {
            return "26.5.3";
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                this.events.add("close " + this.target.name());
            }
        }
    }
}
