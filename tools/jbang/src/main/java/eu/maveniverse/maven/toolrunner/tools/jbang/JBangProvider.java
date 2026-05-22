/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolDetector;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import eu.maveniverse.maven.toolrunner.shared.support.IOTools;
import eu.maveniverse.maven.toolrunner.shared.support.OSTools;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import eu.maveniverse.maven.toolrunner.shared.support.Provisioners;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.*;

/**
 * The JBang tool provider.
 */
public class JBangProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "jbang";

    private static final String EXE_NAME = IS_WINDOWS ? "jbang.cmd" : "jbang";
    private static final String PREFIX = NAME + ".";

    public static final String HOME = PREFIX + "home";
    public static final String CACHE = PREFIX + "cache";
    public static final String CONFIG = PREFIX + "config";

    private static final String ENV_HOME = "JBANG_HOME";
    private static final String ENV_CACHE_DIR = "JBANG_CACHE_DIR";
    private static final String ENV_CONFIG = "JBANG_CONFIG";

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

        // detect from path; if allowed
        if (context.allowPathDetection()) {
            Set<Path> paths =
                    IOTools.dereference(OSTools.which(JBangProvider.EXE_NAME).orElse(Collections.emptySet()));
            // consider only those ending with `bin/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().replace('\\', '/').endsWith("/bin/" + JBangProvider.EXE_NAME)) {
                    tryHome(context, executable.getParent().getParent()).ifPresent(detected::add);
                }
            }
        }

        // detect from installation directory (ie already installed)
        try (Stream<Path> candidateDirectories = Files.list(context.installationDirectory())
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().startsWith(NAME))) {
            candidateDirectories.forEach(p -> {
                try {
                    tryHome(context, p).ifPresent(detected::add);
                } catch (IOException e) {
                    // skip
                }
            });
        }

        return Collections.unmodifiableList(detected);
    }

    /**
     * Collects basic information about discovered JBang home.
     */
    protected Optional<Map<String, String>> tryHome(ToolContext context, Path home) throws IOException {
        Optional<Map<String, String>> maybeExisting = Provisioners.loadMetadata(context, home);
        if (maybeExisting.isPresent()) {
            return maybeExisting;
        }

        // execute and discover information
        Map<String, String> metadata = new HashMap<>();
        metadata.put(JBangProvider.HOME, home.toString());
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
            ToolHandle.Result result = executeTool(
                    context,
                    metadata,
                    ToolExecution.ofCommand("jbang")
                            .addArguments("version", "--verbose")
                            .build());
            if (result.success()) {
                HashMap<String, String> md = new HashMap<>();
                md.put(ToolHandler.TOOL_NAME, NAME);
                md.put(
                        ToolHandler.TOOL_VERSION,
                        result.stdOutString()
                                .orElseThrow(() -> new NoSuchElementException("No value present"))
                                .trim());
                md.put(JBangProvider.HOME, home.toString());
                return Optional.of(md);
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
        boolean isLatest = !metadata.containsKey(ToolHandler.TOOL_VERSION);

        String uri;
        String homePath;
        String version = null;
        if (isLatest) {
            uri = "https://www.jbang.dev/releases/latest/download/jbang.zip";
            homePath = NAME;
        } else {
            version = requireNonNull(metadata.get(ToolHandler.TOOL_VERSION));
            uri = String.format(
                    "https://github.com/jbangdev/jbang/releases/download/v%s/jbang-%s.zip", version, version);
            homePath = NAME + "-" + version;
        }
        Path installDir = context.installationDirectory().resolve(homePath);
        try (FileUtils.TempFile dl = Provisioners.httpGet(context, "github", URI.create(uri))) {
            Provisioners.unpack(context, dl.getPath(), installDir, false);
        }
        Optional<Map<String, String>> provisioned = tryHome(context, installDir);
        if (provisioned.isPresent()) {
            Map<String, String> provisionedMetadata =
                    new HashMap<>(provisioned.orElseThrow(() -> new NoSuchElementException("No value present")));
            if (version == null) {
                Path versionedInstallDir = context.installationDirectory()
                        .resolve(NAME + "-" + requireNonNull(provisionedMetadata.get(ToolHandler.TOOL_VERSION)));
                Files.move(installDir, versionedInstallDir, StandardCopyOption.REPLACE_EXISTING);
                provisionedMetadata.put(HOME, versionedInstallDir.toString());
                installDir = versionedInstallDir;
            }
            Provisioners.saveMetadata(context, installDir, provisionedMetadata);
            return Optional.of(provisionedMetadata);
        }
        return provisioned;
    }

    // ToolExecutor

    @Override
    public List<String> commands(ToolContext context, Map<String, String> metadata) {
        return Collections.singletonList("jbang");
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        if (metadata.containsKey(ToolHandler.TOOL_NAME) && metadata.containsKey(ToolHandler.TOOL_VERSION)) {
            if (!commands(context, metadata).contains(execution.command())) {
                throw new IllegalArgumentException("Unsupported command: " + execution.command());
            }
        }
        String command = execution.command();
        String home = metadata.get(JBangProvider.HOME);
        if (home != null) {
            command = Paths.get(home).resolve("bin").resolve(EXE_NAME).toString();
        }
        return ProcessBuilderExecutor.execute(
                execution.toBuilder().command(command).build(), context.toolTimeout());
    }
}
