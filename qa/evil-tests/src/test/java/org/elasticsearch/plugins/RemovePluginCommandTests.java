/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugins;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.cli.CliTool;
import org.elasticsearch.common.cli.CliToolTestCase;
import org.elasticsearch.common.cli.Terminal;
import org.elasticsearch.common.cli.UserError;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.test.ESTestCase;

@LuceneTestCase.SuppressFileSystems("*")
public class RemovePluginCommandTests extends ESTestCase {

    /** Creates a test environment with bin, config and plugins directories. */
    static Environment createEnv() throws IOException {
        Path home = createTempDir();
        Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("bin").resolve("elasticsearch"));
        Files.createDirectories(home.resolve("plugins"));
        Settings settings = Settings.builder()
            .put("path.home", home)
            .build();
        return new Environment(settings);
    }

    static CliToolTestCase.CaptureOutputTerminal removePlugin(String name, Environment env) throws Exception {
        CliToolTestCase.CaptureOutputTerminal terminal = new CliToolTestCase.CaptureOutputTerminal(Terminal.Verbosity.VERBOSE);
        CliTool.ExitStatus status = new RemovePluginCommand(terminal, name).execute(env.settings(), env);
        assertEquals(CliTool.ExitStatus.OK, status);
        return terminal;
    }

    static void assertRemoveCleaned(Environment env) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(env.pluginsFile())) {
            for (Path file : stream) {
                if (file.getFileName().toString().startsWith(".removing")) {
                    fail("Removal dir still exists, " + file);
                }
            }
        }
    }

    public void testMissing() throws Exception {
        Environment env = createEnv();
        UserError e = expectThrows(UserError.class, () -> {
           removePlugin("dne", env);
        });
        assertTrue(e.getMessage(), e.getMessage().contains("Plugin dne not found"));
        assertRemoveCleaned(env);
    }

    public void testBasic() throws Exception {
        Environment env = createEnv();
        Files.createDirectory(env.pluginsFile().resolve("fake"));
        Files.createFile(env.pluginsFile().resolve("fake").resolve("plugin.jar"));
        Files.createDirectory(env.pluginsFile().resolve("fake").resolve("subdir"));
        Files.createDirectory(env.pluginsFile().resolve("other"));
        removePlugin("fake", env);
        assertFalse(Files.exists(env.pluginsFile().resolve("fake")));
        assertTrue(Files.exists(env.pluginsFile().resolve("other")));
        assertRemoveCleaned(env);
    }

    public void testBin() throws Exception {
        Environment env = createEnv();
        Files.createDirectories(env.pluginsFile().resolve("fake"));
        Path binDir = env.binFile().resolve("fake");
        Files.createDirectories(binDir);
        Files.createFile(binDir.resolve("somescript"));
        removePlugin("fake", env);
        assertFalse(Files.exists(env.pluginsFile().resolve("fake")));
        assertTrue(Files.exists(env.binFile().resolve("elasticsearch")));
        assertFalse(Files.exists(binDir));
        assertRemoveCleaned(env);
    }

    public void testBinNotDir() throws Exception {
        Environment env = createEnv();
        Files.createDirectories(env.pluginsFile().resolve("elasticsearch"));
        UserError e = expectThrows(UserError.class, () -> {
            removePlugin("elasticsearch", env);
        });
        assertTrue(e.getMessage(), e.getMessage().contains("not a directory"));
        assertTrue(Files.exists(env.pluginsFile().resolve("elasticsearch"))); // did not remove
        assertTrue(Files.exists(env.binFile().resolve("elasticsearch")));
        assertRemoveCleaned(env);
    }
}
