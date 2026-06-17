/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.cosign;

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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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

/**
 * The {@code cosign} tool provider.
 */
public class CosignProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "cosign";

    private static final String EXE_NAME = IS_WINDOWS ? "cosign.exe" : "cosign";
    private static final String PREFIX = NAME + ".";

    public static final String HOME = PREFIX + "home";

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
        // $ cosign version
        //  ______   ______        _______. __    _______ .__   __.
        // /      | /  __  \      /       ||  |  /  _____||  \ |  |
        // |  ,----'|  |  |  |    |   (----`|  | |  |  __  |   \|  |
        // |  |     |  |  |  |     \   \    |  | |  | |_ | |  . `  |
        // |  `----.|  `--'  | .----)   |   |  | |  |__| | |  |\   |
        // \______| \______/  |_______/    |__|  \______| |__| \__|
        // cosign: A tool for Container Signing, Verification and Storage in an OCI registry.
        //
        // GitVersion:    v3.0.6
        // GitCommit:     f1ad3ee952313be5d74a49d67ba0aa8d0d5e351f
        // GitTreeState:  "clean"
        // BuildDate:     2026-04-06T21:39:58Z
        // GoVersion:     go1.26.1
        // Compiler:      gc
        // Platform:      linux/amd64
        //
        // $
        try {
            ToolHandle.Result result = executeTool(
                    context,
                    metadata,
                    ToolExecution.ofCommand("cosign").addArguments("version").build());
            if (result.success()) {
                String version = new BufferedReader(new StringReader(result.stdOutString()
                                .orElseThrow(() -> new NoSuchElementException("No value present"))))
                        .lines()
                        .filter(l -> l.startsWith("GitVersion:"))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException("No value present"))
                        .split(":")[1]
                        .trim();
                if (version.startsWith("v")) {
                    version = version.substring(1);
                }
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
        String osName;
        String arch;
        String ext;
        if (Objects.equals("linux", context.detectedOs().get("name"))) {
            osName = "linux";
            arch = context.detectedOs().get("arch");
            if ("x86_64".equals(arch)) {
                arch = "amd64";
            } else if ("aarch_64".equals(arch)) {
                arch = "arm64";
            }
            ext = "";
        } else if (Objects.equals("windows", context.detectedOs().get("name"))
                && Objects.equals("x86_64", context.detectedOs().get("arch"))) {
            osName = "windows";
            arch = "amd64";
            ext = ".exe";
        } else if (Objects.equals("macosx", context.detectedOs().get("name"))) {
            osName = "darwin";
            arch = context.detectedOs().get("arch");
            if ("x86_64".equals(arch)) {
                arch = "amd64";
            } else if ("aarch_64".equals(arch)) {
                arch = "arm64";
            }
            ext = "";
        } else {
            // unsupported OS
            return Optional.empty();
        }

        if (isLatest) {
            version = Provisioners.discoverGHLatest(context, "github", "sigstore", "cosign")
                    .getTag();
            if (version.startsWith("v")) {
                version = version.substring(1);
            }
        } else {
            version = requireNonNull(metadata.get(ToolHandler.TOOL_VERSION));
        }
        String uri = String.format(
                "https://github.com/sigstore/cosign/releases/download/v%s/cosign-%s-%s%s", version, osName, arch, ext);
        String homePath = NAME + "-" + version;
        Path installDir = context.config().installationDirectory().resolve(homePath);
        Path executable = installDir.resolve(EXE_NAME);
        try (FileUtils.TempFile dl = Provisioners.httpGet(context, "github.com", URI.create(uri))) {
            Files.createDirectories(executable.getParent());
            Files.copy(dl.getPath(), executable, StandardCopyOption.REPLACE_EXISTING);
            if (!IS_WINDOWS) {
                executable.toFile().setExecutable(true);
            }
        }
        Optional<Map<String, String>> provisioned = tryHome(context, installDir);
        if (provisioned.isPresent()) {
            Provisioners.saveMetadata(
                    context, installDir, provisioned.orElseThrow(() -> new NoSuchElementException("No value present")));
        }
        return provisioned;
    }

    // ToolExecutor

    @Override
    public List<String> commands(ToolContext context, Map<String, String> metadata) {
        return Collections.singletonList("cosign");
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        String command = execution.command();
        String home = metadata.get(CosignProvider.HOME);
        if (home != null) {
            command = Paths.get(home).resolve(EXE_NAME).toString();
        }
        return ProcessBuilderExecutor.execute(
                execution.toBuilder().command(command).build(),
                context.config().maxRunDuration().toMillis());
    }
}
