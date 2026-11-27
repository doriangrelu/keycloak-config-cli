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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShellLauncherTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "realm dry-run --var shopSecret=s3cr3t | realm dry-run --var shopSecret=***",
                "realm import --var=a=x=y --apply --var b=z | realm import --var=a=*** --apply --var b=***",
                "realm import --var title=\"a b\" --apply | realm import --var title=*** --apply",
                "realm import --var \"title=a b\" --apply | realm import --var \"title=***\" --apply",
                "realm dry-run --var =s3cr3t | realm dry-run --var =***",
                "realm dry-run --var noEqual | realm dry-run --var ***",
                "realm dry-run --var shopSecret:s3cr3t --apply | realm dry-run --var *** --apply",
                "realm dry-run --var=shopSecret:s3cr3t | realm dry-run --var=***",
                "realm dry-run --var \"shopSecret s3cr3t\" | realm dry-run --var ***",
                "realm dry-run --var | realm dry-run --var",
                "realm dry-run --variant x=y | realm dry-run --variant x=y",
                "realm show | realm show"
            })
    void theValuesOfVarAreHidden(final String command, final String shown) {
        assertThat(ShellLauncher.shown(command)).isEqualTo(shown);
    }
}
