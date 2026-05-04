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
import eu.maveniverse.maven.toolrunner.shared.support.OSTools;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import eu.maveniverse.maven.toolrunner.shared.support.Provisioners;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.Artifact;

/**
 * The Maven tool provider.
 */
public class MavenProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "maven";

    private static final String EXE_NAME = IS_WINDOWS ? "mvn.cmd" : "mvn";
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
                    OSTools.dereference(OSTools.which(MavenProvider.EXE_NAME).orElse(Set.of()));
            // consider only those ending with `bin/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().replace("\\", "/").endsWith("/bin/" + MavenProvider.EXE_NAME)) {
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

        return List.copyOf(detected);
    }

    /**
     * Collects basic information about discovered Maven home.
     */
    protected Optional<Map<String, String>> tryHome(ToolContext context, Path mavenHome) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Optional<Map<String, String>> maybeExisting = Provisioners.loadMetadata(context, mavenHome);
        if (maybeExisting.isPresent()) {
            return maybeExisting;
        }

        // execute and discover information
        Map<String, String> metadata = new HashMap<>(Map.of(MavenProvider.HOME, mavenHome.toString()));
        ToolExecution.Builder exe = executionTemplate(context, metadata)
                .addArguments("-v", "-q")
                .stdOut(out)
                .stdErr(err);

        try {
            ToolHandle.Result result = executeTool(context, metadata, exe.build());
            if (result.success()) {
                return Optional.of(Map.of(
                        ToolHandler.TOOL_NAME,
                        MavenProvider.NAME,
                        ToolHandler.TOOL_VERSION,
                        out.toString().trim(),
                        MavenProvider.HOME,
                        mavenHome.toString()));
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
            Provisioners.unpack(context, maybeDistro.orElseThrow().getFile().toPath(), installDir, false);
            Optional<Map<String, String>> provisioned = tryHome(context, installDir);
            if (provisioned.isPresent()) {
                Map<String, String> provisionedMetadata = new HashMap<>(provisioned.orElseThrow());
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
    public ToolExecution.Builder executionTemplate(ToolContext context, Map<String, String> metadata) {
        ToolExecution.Builder builder;
        String mavenHome = metadata.get(MavenProvider.HOME);
        if (mavenHome != null) {
            builder = ToolExecution.ofCommand(
                    Path.of(mavenHome).resolve("bin/" + MavenProvider.EXE_NAME).toString());
        } else {
            builder = ToolExecution.ofCommand(MavenProvider.EXE_NAME);
        }

        return builder;
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        return ProcessBuilderExecutor.execute(execution, context.toolTimeout());
    }
}
