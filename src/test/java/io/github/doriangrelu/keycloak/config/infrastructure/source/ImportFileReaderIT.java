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

package io.github.doriangrelu.keycloak.config.infrastructure.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.github.doriangrelu.keycloak.config.AbstractImportTest;
import io.github.doriangrelu.keycloak.config.application.importation.ImportFile;
import io.github.doriangrelu.keycloak.config.application.importation.InvalidImportException;
import io.github.doriangrelu.keycloak.config.application.importation.port.ImportFileSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.springtest.MockServerTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

@MockServerTest("mockServerUrl=http://localhost:${mockServerPort}")
@SuppressWarnings({"java:S5976"})
class ImportFileReaderIT extends AbstractImportTest {
    private MockServerClient mockServerClient;

    @Test
    void shouldReadLocalFile() {
        String location = "classpath:import-files/import/single/0_create_realm.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(keycloakImport.keySet(), contains(matchesPattern(".+/0_create_realm\\.json$")));
    }

    @Test
    void shouldReadLocalFileLegacy() throws IOException {
        Path realmFile = Files.createTempFile("realm", ".json");
        Path tempFilePath = Files.writeString(realmFile, "{\"enabled\": true, \"realm\": \"realm-sorted-import\"}");

        String importPath = "file:" + tempFilePath.toAbsolutePath();
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, importPath);

        assertThat(keycloakImport, hasKey(is(importPath)));
        assertThat(keycloakImport.get(importPath), contains(importPath));
    }

    @Test
    void shouldReadLocalFilesFromDirectorySorted() {
        String location = "classpath:import-files/import/sorted/*";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);
        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location),
                contains(
                        matchesPattern(".+/0_create_realm\\.json"),
                        matchesPattern(".+/1_update_realm\\.json"),
                        matchesPattern(".+/2_update_realm\\.json"),
                        matchesPattern(".+/4_update_realm\\.json"),
                        matchesPattern(".+/5_update_realm\\.json"),
                        matchesPattern(".+/6_update_realm\\.json"),
                        matchesPattern(".+/7_update_realm\\.json"),
                        matchesPattern(".+/9_update_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromDirectorySortedWithoutHiddenFilesWithRegex() {
        String location = "classpath:import-files/import/sorted/{filename:[^7]+}";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);
        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location),
                contains(
                        matchesPattern(".+/0_create_realm\\.json"),
                        matchesPattern(".+/1_update_realm\\.json"),
                        matchesPattern(".+/2_update_realm\\.json"),
                        matchesPattern(".+/4_update_realm\\.json"),
                        matchesPattern(".+/5_update_realm\\.json"),
                        matchesPattern(".+/6_update_realm\\.json"),
                        matchesPattern(".+/9_update_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromWildcardPattern() {
        String location = "classpath:import-files/import/wildcard/*.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);
        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(keycloakImport.get(location), contains(matchesPattern(".+/0_create_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromWildcardPatternWithDirectoryTraversal() {
        String location = "file:src/test/resources/import-files/import/../import/wildcard/*.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);
        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(keycloakImport.get(location), contains(matchesPattern(".+/0_create_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromRegexPattern() {
        String location = "classpath:import-files/import/wildcard/{filename:.+.json}";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);
        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(keycloakImport.get(location), contains(matchesPattern(".+/0_create_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromDoubleWildcardPattern() {
        String location = "classpath:import-files/import/wildcard/**/*.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);
        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location),
                contains(
                        matchesPattern(".+/0_create_realm\\.json"),
                        matchesPattern(".+/another/directory/1_update_realm\\.json"),
                        matchesPattern(".+/another/directory/2_update_realm\\.json"),
                        matchesPattern(".+/another/directory/3_update_realm\\.json"),
                        matchesPattern(".+/sub/directory/4_update_realm\\.json"),
                        matchesPattern(".+/sub/directory/5_update_realm\\.json"),
                        matchesPattern(".+/sub/directory/6_update_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromManyDirectories() {
        String location1 = "classpath:import-files/import/wildcard/sub/**";
        String location2 = "classpath:import-files/import/wildcard/another/**/*.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location1, location2);
        assertThat(keycloakImport.keySet(), contains(is(location1), is(location2)));
        assertThat(
                keycloakImport.get(location1),
                contains(
                        matchesPattern(".+/sub/directory/4_update_realm\\.json"),
                        matchesPattern(".+/sub/directory/5_update_realm\\.json"),
                        matchesPattern(".+/sub/directory/6_update_realm\\.json"),
                        matchesPattern(".+/sub/directory/7_update_realm\\.yaml")));
        assertThat(
                keycloakImport.get(location2),
                contains(
                        matchesPattern(".+/another/directory/1_update_realm\\.json"),
                        matchesPattern(".+/another/directory/2_update_realm\\.json"),
                        matchesPattern(".+/another/directory/3_update_realm\\.json")));
    }

    @Test
    void shouldReadLocalFilesFromZipArchive() {
        String location = "zip:file:src/test/resources/import-files/import/zip/realm-import.zip!/**/*";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location),
                contains(
                        matchesPattern(".+/0_create_realm\\.json$"),
                        matchesPattern(".+/1_update_realm\\.json$"),
                        matchesPattern(".+/2_update_realm\\.json$"),
                        matchesPattern(".+/3_update_realm\\.json$"),
                        matchesPattern(".+/4_update_realm\\.json$"),
                        matchesPattern(".+/5_update_realm\\.json$")));
    }

    @Test
    void shouldReadLocalFilesFromZipArchiveWithRegex() {
        String location =
                "zip:file:src/test/resources/import-files/import/zip/realm-import.zip!/**/{filename:[^3]+.json}";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location),
                contains(
                        matchesPattern(".+/0_create_realm\\.json$"),
                        matchesPattern(".+/1_update_realm\\.json$"),
                        matchesPattern(".+/2_update_realm\\.json$"),
                        matchesPattern(".+/4_update_realm\\.json$"),
                        matchesPattern(".+/5_update_realm\\.json$")));
    }

    @Test
    void shouldFailOnDirectory() {
        InvalidImportException exception = assertThrows(
                InvalidImportException.class, () -> importFileSource.read(List.of("classpath:import-files/import/")));

        assertThat(exception.getMessage(), is("No files matching 'classpath:import-files/import/'!"));
    }

    @Test
    void shouldFailOnUnknownPath() {
        InvalidImportException exception =
                assertThrows(InvalidImportException.class, () -> importFileSource.read(List.of("classpath::")));

        assertThat(
                exception.getMessage(),
                is(
                        "Unable to proceed resource 'class path resource [:]': class path resource [:] cannot be opened because it does not exist"));
    }

    @Test
    void shouldFailOnInvalidClassPath() {
        InvalidImportException exception = assertThrows(
                InvalidImportException.class, () -> importFileSource.read(List.of("classpath:/invalid/**")));

        assertThat(
                exception.getMessage(),
                is(
                        "Unable to proceed location 'classpath:/invalid/**': class path resource [invalid/] cannot be resolved to URL because it does not exist"));
    }

    @Test
    void shouldFailOnInvalidFile() {
        InvalidImportException exception =
                assertThrows(InvalidImportException.class, () -> importFileSource.read(List.of("invalid")));

        assertThat(
                exception.getMessage(),
                is("Unable to proceed resource 'URL [file:invalid]': invalid (No such file or directory)"));
    }

    @Test
    void shouldReadRemoteFile() {
        mockServerClient.when(request()).respond(this::mockServerResponse);

        String location = mockServerUrl() + "/import/single/0_create_realm.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(keycloakImport.get(location), contains(matchesPattern(".+/0_create_realm\\.json$")));
    }

    @Test
    void shouldReadRemoteFilesFromZipArchive() {
        mockServerClient.when(request()).respond(this::mockServerResponse);

        String location = "zip:" + mockServerUrl() + "/import/zip/realm-import.zip!/**/*";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location),
                contains(
                        matchesPattern(".+/0_create_realm\\.json$"),
                        matchesPattern(".+/1_update_realm\\.json$"),
                        matchesPattern(".+/2_update_realm\\.json$"),
                        matchesPattern(".+/3_update_realm\\.json$"),
                        matchesPattern(".+/4_update_realm\\.json$"),
                        matchesPattern(".+/5_update_realm\\.json$")));
    }

    @Test
    void shouldReadRemoteFileUsingBasicAuth() {
        mockServerClient
                .when(request().withHeaders(header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")))
                .respond(this::mockServerResponse);
        mockServerClient.when(request()).respond(this::mockServerAuthorizationRequiredResponse);

        String userInfo = "user:password";
        String location = mockServerUrl(userInfo) + "/import/single/0_create_realm.json";

        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(
                keycloakImport.get(location), contains(matchesPattern("http://\\*\\*\\*@.+/0_create_realm\\.json$")));
    }

    @Test
    void shouldReadRemoteFileAndIgnoreIncompleteBasicAuth() {
        mockServerClient.when(request()).respond(this::mockServerResponse);

        String userInfo = "user";
        String location = mockServerUrl(userInfo) + "/import/single/0_create_realm.json";
        Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

        assertThat(keycloakImport, hasKey(is(location)));
        assertThat(keycloakImport.get(location), contains(matchesPattern(".+/0_create_realm\\.json$")));
    }

    private String mockServerUrl(String userInfo) {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("http");
        builder.setHost(mockServerClient.remoteAddress().getAddress().getHostAddress());
        builder.setPort(mockServerClient.getPort());
        builder.setUserInfo(userInfo);
        return builder.toString();
    }

    private String mockServerUrl() {
        return mockServerUrl(null);
    }

    @Nested
    @TestPropertySource(properties = {"import.files.include-hidden-files=true"})
    class HiddenFilesTrue extends AbstractImportTest {
        @Test
        void shouldReadLocalFilesFromDirectorySorted() {
            String location = "classpath:import-files/import/sorted/*";
            Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

            assertThat(keycloakImport, hasKey(is(location)));
            assertThat(
                    keycloakImport.get(location),
                    contains(
                            matchesPattern(".+/.3_update_realm\\.json"),
                            matchesPattern(".+/.8_update_realm\\.json"),
                            matchesPattern(".+/0_create_realm\\.json"),
                            matchesPattern(".+/1_update_realm\\.json"),
                            matchesPattern(".+/2_update_realm\\.json"),
                            matchesPattern(".+/4_update_realm\\.json"),
                            matchesPattern(".+/5_update_realm\\.json"),
                            matchesPattern(".+/6_update_realm\\.json"),
                            matchesPattern(".+/7_update_realm\\.json"),
                            matchesPattern(".+/9_update_realm\\.json")));
        }
    }

    @Nested
    @TestPropertySource(properties = {"import.files.excludes=**/*create*,**/4_*,src"})
    class Exclude extends AbstractImportTest {
        @Test
        void shouldReadLocalFilesFromDirectorySorted() {
            String location = "classpath:import-files/import/sorted/*";
            Map<String, List<String>> keycloakImport = filesByLocation(importFileSource, location);

            assertThat(keycloakImport, hasKey(is(location)));
            assertThat(
                    keycloakImport.get(location),
                    contains(
                            matchesPattern(".+/1_update_realm\\.json"),
                            matchesPattern(".+/2_update_realm\\.json"),
                            matchesPattern(".+/5_update_realm\\.json"),
                            matchesPattern(".+/6_update_realm\\.json"),
                            matchesPattern(".+/7_update_realm\\.json"),
                            matchesPattern(".+/9_update_realm\\.json")));
        }
    }

    /** The names of the files read, by location. */
    private static Map<String, List<String>> filesByLocation(final ImportFileSource source, final String... locations) {
        Map<String, List<String>> files = new LinkedHashMap<>();
        for (ImportFile file : source.read(List.of(locations))) {
            files.computeIfAbsent(file.location(), unused -> new ArrayList<>()).add(file.name());
        }
        return files;
    }

    private HttpResponse mockServerResponse(HttpRequest request) throws IOException {
        return response()
                .withBody(IOUtils.toByteArray(
                        new ClassPathResource("import-files" + request.getPath().getValue()).getInputStream()));
    }

    private HttpResponse mockServerAuthorizationRequiredResponse(HttpRequest request) {
        return response()
                .withHeader("WWW-Authenticate", "Basic realm=\"protected\"")
                .withStatusCode(401);
    }
}
