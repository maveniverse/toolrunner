/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolDetector;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import eu.maveniverse.maven.toolrunner.shared.support.OSTools;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The JBang tool detector.
 */
public class JBangDetector implements ToolDetector {
    private final JBangExecutor jbangExecutor;

    public JBangDetector(JBangExecutor jbangExecutor) {
        this.jbangExecutor = requireNonNull(jbangExecutor);
    }

    @Override
    public List<Map<String, String>> detectTool() throws IOException {
        ArrayList<Map<String, String>> detected = new ArrayList<>();
        try {
            // detect from path
            Set<Path> paths =
                    OSTools.dereference(OSTools.which(JBangProvider.EXE_NAME).orElse(Set.of()));
            // consider only those ending with `bin/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().endsWith("/bin/" + JBangProvider.EXE_NAME)) {
                    tryHome(executable.toString().replace("/bin/" + JBangProvider.EXE_NAME, ""))
                            .ifPresent(detected::add);
                }
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        return List.copyOf(detected);
    }

    /**
     * Collects basic information about discovered JBang home.
     */
    protected Optional<Map<String, String>> tryHome(String jbangHome) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ToolExecution.Builder exe = jbangExecutor
                .executionTemplate(Map.of(JBangProvider.HOME, jbangHome))
                .addArguments("version", "--verbose")
                .stdOut(out)
                .stdErr(err);
        // TODO: maybe collect more info?
        // $ jbang version --verbose
        // [jbang] [0:150] jbang version 0.138.0
        // Cache: /home/cstamas/.jbang/cache
        // Config: /home/cstamas/.jbang
        // Repository: /home/cstamas/.m2/repository
        // Java: /home/cstamas/.sdkman/candidates/java/21.0.11-tem [21.0.11]
        // OS: linux
        // Arch: x64
        // Shell: bash
        // Native Image: false
        // 0.138.0             <<< STDOUT, the rest is STDERR!

        ToolExecution.Result result = jbangExecutor.executeTool(Map.of(), exe.build());
        if (result.success()) {
            return Optional.of(Map.of(
                    ToolProvider.TOOL_NAME,
                    JBangProvider.NAME,
                    ToolProvider.TOOL_VERSION,
                    out.toString().trim(),
                    JBangProvider.HOME,
                    jbangHome));
        }
        return Optional.empty();
    }
}
