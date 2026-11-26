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

package io.github.doriangrelu.keycloak.config.infrastructure.source.targets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.doriangrelu.keycloak.config.domain.definition.GrantType;
import io.github.doriangrelu.keycloak.config.domain.definition.Target;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetsFileTest {

    private static final String PREFIX = "kcc-targets.yaml: target 'dev': ";

    @TempDir
    Path root;

    private TargetsFile with(final String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/targets/" + resource)) {
            Files.write(root.resolve("kcc-targets.yaml"), in.readAllBytes());
        }
        return new TargetsFile(root);
    }

    @Test
    void targetsAreReadInOrder() throws IOException {
        TargetsFile targets = with("two-targets.yaml");

        List<Target> all = targets.all();

        assertThat(all).extracting(Target::name).containsExactly("dev", "local");
        assertThat(all.get(0))
                .isEqualTo(new Target(
                        "dev",
                        "https://dev.example.com",
                        "ops",
                        "cli",
                        GrantType.PASSWORD,
                        Optional.of("admin"),
                        false,
                        Duration.ofMillis(500)));
        assertThat(targets.find("local")).map(Target::url).contains("http://localhost:8080");
        assertThat(targets.find("nope")).isEmpty();
    }

    @Test
    void defaultsAreApplied() throws IOException {
        Target local = with("two-targets.yaml").find("local").orElseThrow();

        assertThat(local.loginRealm()).isEqualTo("master");
        assertThat(local.clientId()).isEqualTo("admin-cli");
        assertThat(local.grantType()).isEqualTo(GrantType.PASSWORD);
        assertThat(local.sslVerify()).isTrue();
        assertThat(local.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void clientCredentialsNeedNoUser() throws IOException {
        Target ci = with("client-credentials.yaml").find("ci").orElseThrow();

        assertThat(ci.grantType()).isEqualTo(GrantType.CLIENT_CREDENTIALS);
        assertThat(ci.user()).isEmpty();
        assertThat(ci.clientId()).isEqualTo("kcc");
    }

    @Test
    void aPasswordTargetWithoutUserIsRefused() throws IOException {
        TargetsFile targets = with("password-without-user.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith(PREFIX)
                .hasMessageContaining("'user'");
    }

    @Test
    void anUnknownFieldIsRefused() throws IOException {
        TargetsFile targets = with("unknown-field.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith(PREFIX)
                .hasMessageContaining("colour");
    }

    @Test
    void aMissingUrlIsRefused() throws IOException {
        TargetsFile targets = with("missing-url.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith(PREFIX)
                .hasMessageContaining("'url'");
    }

    @Test
    void anUnknownGrantTypeIsRefused() throws IOException {
        TargetsFile targets = with("unknown-grant.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith(PREFIX)
                .hasMessageContaining("'grant-type'")
                .hasMessageContaining("magic");
    }

    @Test
    void noFileMeansNoTarget() {
        TargetsFile targets = new TargetsFile(root);

        assertThat(targets.all()).isEmpty();
        assertThat(targets.find("dev")).isEmpty();
    }

    @Test
    void aSecretFieldIsRefused() throws IOException {
        TargetsFile password = with("password-field.yaml");
        assertThatThrownBy(password::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith(PREFIX)
                .hasMessageContaining("secrets do not belong in kcc-targets.yaml")
                .hasMessageNotContaining("hunter2");

        TargetsFile secret = with("client-secret-field.yaml");
        assertThatThrownBy(secret::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageContaining("secrets do not belong in kcc-targets.yaml")
                .hasMessageNotContaining("hunter2");
    }

    @Test
    void anInvalidTimeoutNamesTheTargetAndTheField() throws IOException {
        TargetsFile targets = with("bad-timeout.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith(PREFIX)
                .hasMessageContaining("'connect-timeout'");
    }

    @Test
    void aTargetNameWithOtherCharactersIsRefused() throws IOException {
        TargetsFile targets = with("bad-name.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith("kcc-targets.yaml: target 'my target': ");
    }

    @Test
    void aDuplicateTargetIsRefused() throws IOException {
        TargetsFile targets = with("duplicate.yaml");

        assertThatThrownBy(targets::all)
                .isInstanceOf(InvalidTargetsFileException.class)
                .hasMessageStartingWith("kcc-targets.yaml: ")
                .hasMessageContaining("duplicate");
    }
}
