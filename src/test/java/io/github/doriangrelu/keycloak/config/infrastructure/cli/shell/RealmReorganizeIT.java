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
import static org.springframework.shell.test.ShellInputProvider.providerFor;

import io.github.doriangrelu.keycloak.config.application.definition.ReadRealms;
import io.github.doriangrelu.keycloak.config.application.definition.RuleBook;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.CommandLineConfiguration;
import io.github.doriangrelu.keycloak.config.infrastructure.cli.ShellAdaptersConfiguration;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.test.ShellTestClient;

/** The realm reorganize command: through the real launcher in script mode, and through the interactive shell. */
class RealmReorganizeIT {

    private static final Path FIXTURES = Path.of("src/test/resources/definition/reorganize");

    @TempDir
    Path root;

    private ApplicationContextRunner contexts() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration.class))
                .withUserConfiguration(
                        CommandLineConfiguration.class,
                        ShellCommandConfiguration.class,
                        ShellAdaptersConfiguration.class)
                .withPropertyValues(
                        "run.operation=SHELL",
                        "shell.root=" + this.root.toString().replace('\\', '/'));
    }

    /** What a script command gave: the exit code and what it printed. */
    private record Outcome(int exitCode, String output) {}

    private static Outcome script(final AssertableApplicationContext context, final String command) {
        StringWriter output = new StringWriter();
        ShellLauncher launcher = new ShellLauncher(
                new ShellProperties(null, null, command, "demo", null),
                context.getBean(RuleBook.class),
                context.getBean(ReadRealms.class),
                context.getBean(ShellSession.class),
                context.getBean(CommandParser.class),
                context.getBean(CommandRegistry.class),
                context.getBeanProvider(ShellRunner.class),
                new PrintWriter(output, true));
        launcher.run(new DefaultApplicationArguments());
        return new Outcome(launcher.getExitCode(), output.toString());
    }

    private void copy(final String fixture) throws IOException {
        Path source = FIXTURES.resolve(fixture);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = this.root.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
    }

    private List<String> names() throws IOException {
        try (Stream<Path> children = Files.list(this.root.resolve("demo"))) {
            return children.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private String read(final String name) throws IOException {
        return Files.readString(this.root.resolve("demo").resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void thePlanIsShownAndNothingIsWrittenWithoutApply() throws IOException {
        copy("monolithic-yaml");
        contexts().run(context -> {
            script(context, "realm format");
            Outcome outcome = script(context, "realm reorganize");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .contains("Moves planned:")
                    .contains("  clients (2)  demo.yaml -> 20-clients.yaml")
                    .contains("  demo.yaml -> 00-realm.yaml (what is left)")
                    .contains("Comments around moved elements may be lost. Commit your files first, then run a dry"
                            + " run after.")
                    .contains("Apply these moves?");
            assertThat(outcome.output().stripTrailing()).endsWith("Nothing written: run again with --apply");
            assertThat(names()).containsExactly("demo.yaml");
        });
    }

    @Test
    void applyReorganizesTheFolder() throws IOException {
        copy("monolithic-yaml");
        contexts().run(context -> {
            script(context, "realm format");
            Outcome outcome = script(context, "realm reorganize --apply");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output())
                    .doesNotContain("Apply these moves?")
                    .contains("Realm 'demo' reorganized: 5 files written, 0 removed");
            assertThat(names())
                    .containsExactly(
                            "00-realm.yaml", "20-clients.yaml", "30-roles.yaml", "40-groups.yaml", "50-links.yaml");
            assertThat(read("20-clients.yaml")).contains("\"{{appUrl}}/*\"");
        });
    }

    @Test
    void theQuestionAppliesWhenAnsweredYes() throws IOException {
        copy("monolithic-yaml");
        contexts().run(context -> {
            ShellTestClient shell =
                    new ShellTestClient(context.getBean(CommandParser.class), context.getBean(CommandRegistry.class));
            shell.sendCommand("realm open demo");
            shell.sendCommand("realm format");

            shell.sendCommand(providerFor("realm reorganize").withInput("y").build());

            assertThat(names()).contains("20-clients.yaml").doesNotContain("demo.yaml");
        });
    }

    @Test
    void aStandardFolderSaysNothingToMove() throws IOException {
        copy("already-standard");
        contexts().run(context -> {
            script(context, "realm format");
            Outcome outcome = script(context, "realm reorganize");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.output()).contains("Nothing to move").doesNotContain("Apply these moves?");
        });
    }

    @Test
    void anUnformattedRealmIsRefused() throws IOException {
        copy("monolithic-yaml");
        Files.writeString(this.root.resolve("demo/demo.yaml"), "realm:   demo\n\n\nenabled: true\n");
        contexts().run(context -> {
            Outcome outcome = script(context, "realm reorganize --apply");

            assertThat(outcome.exitCode()).isEqualTo(2);
            assertThat(outcome.output()).contains("Run 'realm format' first");
            assertThat(names()).containsExactly("demo.yaml");
        });
    }
}
