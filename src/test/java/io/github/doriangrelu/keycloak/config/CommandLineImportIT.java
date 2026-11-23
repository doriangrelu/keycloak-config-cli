/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
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

package io.github.doriangrelu.keycloak.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.github.doriangrelu.keycloak.config.infrastructure.cli.ExitCode;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ImportRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

class CommandLineImportIT extends AbstractImportIT {

    private final Runtime runtime = spy(Runtime.getRuntime());

    @Nested
    @ContextConfiguration()
    class CommandLineImportFilesIT extends AbstractImportIT {
        @Test
        void testImportFile() {
            try (MockedStatic<Runtime> runtimeUtil = mockStatic(Runtime.class)) {
                runtimeUtil.when(Runtime::getRuntime).thenReturn(runtime);
                doNothing().when(runtime).exit(anyInt());

                KeycloakConfigApplication.main(
                        new String[] {"--import.files.locations=src/test/resources/import-files/cli/file.json"});

                RealmRepresentation fileRealm =
                        keycloakProvider.getInstance().realm("file").toRepresentation();

                assertThat(fileRealm.getRealm(), is("file"));
                assertThat(fileRealm.isEnabled(), is(true));

                verify(runtime).exit(0);
            }
        }

        @Test
        void testImportDirectory() {
            try (MockedStatic<Runtime> runtimeUtil = mockStatic(Runtime.class)) {
                runtimeUtil.when(Runtime::getRuntime).thenReturn(runtime);
                doNothing().when(runtime).exit(anyInt());

                KeycloakConfigApplication.main(new String[] {
                    "--keycloak.sslVerify=false", "--import.files.locations=src/test/resources/import-files/cli/dir/*"
                });

                RealmRepresentation file1Realm =
                        keycloakProvider.getInstance().realm("file1").toRepresentation();

                assertThat(file1Realm.getRealm(), is("file1"));
                assertThat(file1Realm.isEnabled(), is(true));

                RealmRepresentation file2Realm =
                        keycloakProvider.getInstance().realm("file2").toRepresentation();

                assertThat(file2Realm.getRealm(), is("file2"));
                assertThat(file2Realm.isEnabled(), is(true));

                verify(runtime).exit(0);
            }
        }

        @Test
        void testImportInvalid() {
            try (MockedStatic<Runtime> runtimeUtil = mockStatic(Runtime.class)) {
                runtimeUtil.when(Runtime::getRuntime).thenReturn(runtime);
                doNothing().when(runtime).exit(anyInt());

                KeycloakConfigApplication.main(new String[] {
                    "--import.files.locations=invalid",
                    "--logging.level.io.github.doriangrelu.keycloak.config.infrastructure.cli.ImportRunner=error",
                });

                verify(runtime).exit(ExitCode.INVALID_CONFIGURATION.code());
            }
        }
    }

    @Nested
    @ContextConfiguration()
    @TestPropertySource(
            properties = {
                "import.files.locations=src/test/resources/application-IT.properties",
            })
    class CommandLineImportInvalidIT extends AbstractImportIT {
        @Autowired
        ImportRunner runner;

        @Test
        void testInvalidFileFormatException() {
            runner.run();

            assertThat(runner.getExitCode(), is(ExitCode.INVALID_CONFIGURATION.code()));
        }
    }

    @Nested
    @ContextConfiguration()
    @TestPropertySource(properties = {"import.files.locations=invalid"})
    class CommandLineImportNotExistsExceptionIT extends AbstractImportIT {
        @Autowired
        ImportRunner runner;

        @Test
        void testInvalidImportException() {
            runner.run();

            assertThat(runner.getExitCode(), is(ExitCode.INVALID_CONFIGURATION.code()));
        }
    }
}
