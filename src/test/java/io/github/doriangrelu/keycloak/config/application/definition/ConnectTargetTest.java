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

package io.github.doriangrelu.keycloak.config.application.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.application.definition.port.KeycloakConnector;
import io.github.doriangrelu.keycloak.config.application.definition.port.Targets;
import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectTargetTest {

    private static final Target DEV = target("dev");
    private static final Target PROD = target("prod");

    private final List<Target> asked = new ArrayList<>();
    private final List<String> secrets = new ArrayList<>();
    private final Connection opened = new Connection() {
        @Override
        public Target target() {
            return DEV;
        }

        @Override
        public String keycloakVersion() {
            return "26.5.3";
        }

        @Override
        public void close() {}
    };

    private final KeycloakConnector connector = (target, secret) -> {
        secrets.add(secret);
        return opened;
    };

    private static Target target(final String name) {
        return new Target(
                name,
                "https://" + name + ".example.org",
                "master",
                "cli",
                GrantType.PASSWORD,
                Optional.of("admin"),
                true,
                Duration.ofSeconds(5));
    }

    private static Targets targets(final Target... all) {
        return new Targets() {
            @Override
            public List<Target> all() {
                return List.of(all);
            }

            @Override
            public Optional<Target> find(final String name) {
                return List.of(all).stream().filter(t -> t.name().equals(name)).findFirst();
            }
        };
    }

    @Test
    void anUnknownTargetListsTheKnownOnes() {
        ConnectTarget use = new ConnectTarget(targets(DEV, PROD), connector);

        assertThatThrownBy(() -> use.connect("staging", t -> "s"))
                .isInstanceOf(UnknownTargetException.class)
                .hasMessage("Unknown target 'staging': known targets are dev, prod");
        assertThat(secrets).isEmpty();
    }

    @Test
    void anUnknownTargetWithoutAnyTargetSaysSo() {
        ConnectTarget use = new ConnectTarget(targets(), connector);

        assertThatThrownBy(() -> use.connect("dev", t -> "s"))
                .isInstanceOf(UnknownTargetException.class)
                .hasMessage("Unknown target 'dev': no target in kcc-targets.yaml");
    }

    @Test
    void theSecretIsAskedForTheTarget() {
        ConnectTarget use = new ConnectTarget(targets(DEV, PROD), connector);

        use.connect("prod", t -> {
            asked.add(t);
            return "s3cret";
        });

        assertThat(asked).containsExactly(PROD);
    }

    @Test
    void theConnectorReceivesTheTarget() {
        List<Target> received = new ArrayList<>();
        KeycloakConnector recording = (target, secret) -> {
            received.add(target);
            secrets.add(secret);
            return opened;
        };
        ConnectTarget use = new ConnectTarget(targets(DEV, PROD), recording);

        Connection connection = use.connect("dev", t -> "s3cret");

        assertThat(connection).isSameAs(opened);
        assertThat(received).containsExactly(DEV);
        assertThat(secrets).containsExactly("s3cret");
    }

    @Test
    void theTargetsAreThoseOfTheFile() {
        assertThat(new ConnectTarget(targets(DEV, PROD), connector).targets()).containsExactly(DEV, PROD);
    }
}
