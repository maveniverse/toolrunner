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
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolDetector;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import eu.maveniverse.maven.toolrunner.shared.support.OSTools;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import eu.maveniverse.maven.toolrunner.shared.support.Provisioners;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The JBang tool provider.
 */
public class JBangProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "jbang";

    private static final String EXE_NAME = IS_WINDOWS ? NAME + ".cmd" : NAME;
    private static final String PREFIX = NAME + ".";

    public static final String HOME = PREFIX + "home";
    public static final String CACHE = PREFIX + "cache";
    public static final String CONFIG = PREFIX + "config";

    // ToolProvider

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDetector toolDetector() {
        return this;
    }

    @Override
    public Optional<ToolProvisioner> toolProvisioner() {
        return Optional.of(this);
    }

    @Override
    public ToolExecutor toolExecutor() {
        return this;
    }

    // ToolDetector

    @Override
    public List<Map<String, String>> detectTool(ToolContext context) throws IOException {
        ArrayList<Map<String, String>> detected = new ArrayList<>();
        // detect from path
        Set<Path> paths =
                OSTools.dereference(OSTools.which(JBangProvider.EXE_NAME).orElse(Set.of()));
        // consider only those ending with `bin/$EXE_NAME`
        for (Path executable : paths) {
            if (executable.toString().endsWith("/bin/" + JBangProvider.EXE_NAME)) {
                tryHome(context, executable.toString().replace("/bin/" + JBangProvider.EXE_NAME, ""))
                        .ifPresent(detected::add);
            }
        }
        return List.copyOf(detected);
    }

    /**
     * Collects basic information about discovered JBang home.
     */
    protected Optional<Map<String, String>> tryHome(ToolContext context, String jBangHome) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Map<String, String> metadata = new HashMap<>(Map.of(JBangProvider.HOME, jBangHome));
        ToolExecution.Builder exe = executionTemplate(context, metadata)
                .addArguments("version", "--verbose")
                .stdOut(out)
                .stdErr(err);
        // TODO: maybe collect more info?
        // $ jbang version --verbose
        // err:
        // [jbang] [0:150] jbang version 0.138.0
        // Cache: /home/cstamas/.jbang/cache
        // Config: /home/cstamas/.jbang
        // Repository: /home/cstamas/.m2/repository
        // Java: /home/cstamas/.sdkman/candidates/java/21.0.11-tem [21.0.11]
        // OS: linux
        // Arch: x64
        // Shell: bash
        // Native Image: false
        // out:
        // 0.138.0

        try {
            ToolHandle.Result result = executeTool(context, metadata, exe.build());
            if (result.success()) {
                return Optional.of(Map.of(
                        ToolProvider.TOOL_NAME,
                        JBangProvider.NAME,
                        ToolProvider.TOOL_VERSION,
                        out.toString().trim(),
                        JBangProvider.HOME,
                        jBangHome));
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // ToolProvisioner

    @Override
    public Optional<Map<String, String>> provisionTool(ToolContext context, Map<String, String> metadata)
            throws IOException {
        boolean isLatest = metadata == ToolProvider.DEFAULT_METADATA;

        String uri;
        String homePath;
        if (isLatest) {
            uri = "https://www.jbang.dev/releases/latest/download/jbang.zip";
            homePath = "jbang";
        } else {
            String version = requireNonNull(metadata.get(ToolProvider.TOOL_VERSION));
            uri = String.format(
                    "https://github.com/jbangdev/jbang/releases/download/v%s/jbang-%s.zip", version, version);
            homePath = "jbang-" + version;
        }
        Path installDir = context.effectiveConfig()
                .installationDirectory()
                .resolve(homePath)
                .toAbsolutePath();
        Path dl = Provisioners.httpGet(context, "github", URI.create(uri));
        Provisioners.unpack(dl, installDir);
        return tryHome(context, installDir.toString());
    }

    // ToolExecutor

    @Override
    public ToolExecution.Builder executionTemplate(ToolContext context, Map<String, String> metadata) {
        ToolExecution.Builder builder;
        String jbangHome = metadata.get(JBangProvider.HOME);
        if (jbangHome != null) {
            builder = ToolExecution.ofCommand(
                    Path.of(jbangHome).resolve("bin/" + JBangProvider.EXE_NAME).toString());
            builder.environmentVariable("JBANG_HOME", jbangHome);
        } else {
            builder = ToolExecution.ofCommand(JBangProvider.EXE_NAME);
        }

        if (metadata.containsKey(JBangProvider.CACHE)) {
            builder.environmentVariable("JBANG_CACHE_DIR", metadata.get(JBangProvider.CACHE));
        }

        if (metadata.containsKey(JBangProvider.CONFIG)) {
            builder.environmentVariable("JBANG_CONFIG", metadata.get(JBangProvider.CONFIG));
        }

        return builder;
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        return ProcessBuilderExecutor.execute(execution);
    }
}
