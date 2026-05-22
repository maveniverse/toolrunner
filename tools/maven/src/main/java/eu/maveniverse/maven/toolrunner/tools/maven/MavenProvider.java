/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.maven;

import static java.util.Objects.requireNonNull;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.Artifact;

/**
 * The Apache Maven tool provider.
 */
public class MavenProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "maven";

    private static final Map<String, String> EXE_NAMES;

    static {
        Map<String, String> exe = new HashMap<>();
        exe.put("mvn", IS_WINDOWS ? "mvn.cmd" : "mvn");
        exe.put("mvnenc", IS_WINDOWS ? "mvnenc.cmd" : "mvn");
        exe.put("mvnup", IS_WINDOWS ? "mvnup.cmd" : "mvn");
        EXE_NAMES = Collections.unmodifiableMap(exe);
    }

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
        if (context.allowPathDetection()) {
            Set<Path> paths =
                    IOTools.dereference(OSTools.which(EXE_NAMES.get("mvn")).orElse(Collections.emptySet()));
            // consider only those ending with `bin/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().replace("\\", "/").endsWith("/bin/" + EXE_NAMES.get("mvn"))) {
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
     * Collects basic information about discovered Maven home.
     */
    protected Optional<Map<String, String>> tryHome(ToolContext context, Path home) throws IOException {
        Optional<Map<String, String>> maybeExisting = Provisioners.loadMetadata(context, home);
        if (maybeExisting.isPresent()) {
            return maybeExisting;
        }

        // execute and discover information
        Map<String, String> metadata = new HashMap<>();
        metadata.put(MavenProvider.HOME, home.toString());
        try {
            ToolHandle.Result result = executeTool(
                    context,
                    metadata,
                    ToolExecution.ofCommand("mvn").addArguments("-v", "-q").build());
            if (result.success()) {
                HashMap<String, String> md = new HashMap<>();
                md.put(ToolHandler.TOOL_NAME, NAME);
                md.put(
                        ToolHandler.TOOL_VERSION,
                        result.stdOutString()
                                .orElseThrow(() -> new NoSuchElementException("No value present"))
                                .trim());
                md.put(MavenProvider.HOME, home.toString());
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

        String homePath;
        String version;
        if (isLatest) {
            version = Provisioners.RELEASE_VERSION;
            homePath = NAME;
        } else {
            version = requireNonNull(metadata.get(ToolHandler.TOOL_VERSION));
            homePath = NAME + "-" + version;
        }
        Path installDir = context.installationDirectory().resolve(homePath);
        Optional<Artifact> maybeDistro =
                Provisioners.resolveArtifact(context, "org.apache.maven:apache-maven:zip:bin:" + version);
        if (maybeDistro.isPresent()) {
            Provisioners.unpack(
                    context,
                    maybeDistro
                            .orElseThrow(() -> new NoSuchElementException("No value present"))
                            .getFile()
                            .toPath(),
                    installDir,
                    false);
            Optional<Map<String, String>> provisioned = tryHome(context, installDir);
            if (provisioned.isPresent()) {
                Map<String, String> provisionedMetadata =
                        new HashMap<>(provisioned.orElseThrow(() -> new NoSuchElementException("No value present")));
                if (Provisioners.RELEASE_VERSION.equals(version)) {
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
        return Optional.empty();
    }

    // ToolExecutor

    @Override
    public Set<String> commands(ToolContext context, Map<String, String> metadata) {
        String toolVersion = requireNonNull(metadata.get(ToolHandler.TOOL_VERSION));
        if (toolVersion.startsWith("3.")) {
            return Collections.singleton("mvn");
        } else {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList("mvn", "mvnup", "mvnenc")));
        }
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
        String home = metadata.get(MavenProvider.HOME);
        if (home != null) {
            command = Paths.get(home)
                    .resolve("bin")
                    .resolve(EXE_NAMES.get(command))
                    .toString();
        }
        return ProcessBuilderExecutor.execute(
                execution.toBuilder().command(command).build(), context.toolTimeout());
    }
}
