/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.minisign;

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code minisign} tool provider.
 */
public class MinisignProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "minisign";

    private static final String EXE_NAME = IS_WINDOWS ? "minisign.exe" : "minisign";
    private static final String PREFIX = NAME + ".";

    public static final String HOME = PREFIX + "home";

    private final Logger log = LoggerFactory.getLogger(this.getClass());

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
        if (context.config().allowOsPathEnvDetection()) {
            Set<Path> paths = IOTools.dereference(OSTools.which(EXE_NAME).orElse(Collections.emptySet()));
            // consider only those ending with `/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().replace("\\", "/").endsWith(EXE_NAME)) {
                    tryHome(context, executable.getParent()).ifPresent(detected::add);
                }
            }
        }

        // detect from installation directory (ie already installed)
        try (Stream<Path> candidateDirectories = Files.list(context.config().installationDirectory())
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
        metadata.put(HOME, home.toString());
        // TODO: maybe collect more info?
        // $ minisign -v
        // minisign 0.12
        // $
        try {
            ToolHandle.Result result = executeTool(
                    context,
                    metadata,
                    ToolExecution.ofCommand("minisign").addArguments("-v").build());
            if (result.success()) {
                String[] versions = result.stdOutString()
                        .orElseThrow(() -> new NoSuchElementException("No value present"))
                        .trim()
                        .split(" ");
                String version = versions[1];
                HashMap<String, String> md = new HashMap<>();
                md.put(ToolHandler.TOOL_NAME, NAME);
                md.put(ToolHandler.TOOL_VERSION, version);
                md.put(HOME, home.toString());
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

        String version;
        String clsext;
        if (Objects.equals("linux", context.detectedOs().get("name"))) {
            clsext = "linux.tar.gz";
        } else if (Objects.equals("windows", context.detectedOs().get("name"))
                && Objects.equals("64", context.detectedOs().get("bitness"))) {
            clsext = "win64.zip";
        } else if (Objects.equals("macosx", context.detectedOs().get("name"))) {
            clsext = "macos.zip";
        } else {
            // unsupported OS
            return Optional.empty();
        }

        if (isLatest) {
            version = Provisioners.discoverGHLatest(context, "github", "jedisct1", "minisign")
                    .getTag();
        } else {
            version = requireNonNull(metadata.get(ToolHandler.TOOL_VERSION));
        }
        String uri = String.format(
                "https://github.com/jedisct1/minisign/releases/download/%s/minisign-%s-%s", version, version, clsext);
        log.debug("Downloading minisign from {}", uri);
        String homePath = NAME + "-" + version;
        Path installDir = context.config().installationDirectory().resolve(homePath);
        try (FileUtils.TempFile dl = Provisioners.httpGet(context, "github.com", URI.create(uri))) {
            Provisioners.unpack(context, dl.getPath(), installDir, false);
        }
        // shuffle around
        if (!Objects.equals("macosx", context.detectedOs().get("name"))) {
            String arch = context.detectedOs().get("arch");
            Files.move(installDir.resolve(arch).resolve(EXE_NAME), installDir.resolve(EXE_NAME));
            if (!IS_WINDOWS) {
                installDir.resolve(EXE_NAME).toFile().setExecutable(true);
            }
        }
        Optional<Map<String, String>> provisioned = tryHome(context, installDir);
        if (provisioned.isPresent()) {
            Map<String, String> provisionedMetadata =
                    new HashMap<>(provisioned.orElseThrow(() -> new NoSuchElementException("No value present")));
            Path versionedInstallDir = context.config()
                    .installationDirectory()
                    .resolve(NAME + "-" + requireNonNull(provisionedMetadata.get(ToolHandler.TOOL_VERSION)));
            Files.move(installDir, versionedInstallDir, StandardCopyOption.REPLACE_EXISTING);
            provisionedMetadata.put(HOME, versionedInstallDir.toString());
            installDir = versionedInstallDir;
            Provisioners.saveMetadata(context, installDir, provisionedMetadata);
            return Optional.of(provisionedMetadata);
        }
        return provisioned;
    }

    // ToolExecutor

    @Override
    public List<String> commands(ToolContext context, Map<String, String> metadata) {
        return Collections.singletonList("minisign");
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        String command = execution.command();
        String home = metadata.get(MinisignProvider.HOME);
        if (home != null) {
            command = Paths.get(home).resolve(EXE_NAME).toString();
        }
        return ProcessBuilderExecutor.execute(
                execution.toBuilder().command(command).build(),
                context.config().maxRunDuration().toMillis());
    }
}
