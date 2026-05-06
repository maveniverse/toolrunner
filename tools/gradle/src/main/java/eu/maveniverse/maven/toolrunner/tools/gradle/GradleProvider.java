/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.gradle;

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
import eu.maveniverse.maven.toolrunner.shared.support.OSTools;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import eu.maveniverse.maven.toolrunner.shared.support.Provisioners;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
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
import javax.swing.*;

/**
 * The Gradle tool provider.
 */
public class GradleProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "gradle";

    private static final String EXE_NAME = IS_WINDOWS ? "gradle.bat" : "gradle";
    private static final String PREFIX = NAME + ".";

    public static final String HOME = PREFIX + "home";

    private static final String ENV_HOME = "GRADLE_HOME";

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
                    OSTools.dereference(OSTools.which(GradleProvider.EXE_NAME).orElse(Set.of()));
            // consider only those ending with `bin/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().replace("\\", "/").endsWith("/bin/" + GradleProvider.EXE_NAME)) {
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
     * Collects basic information about discovered JBang home.
     */
    protected Optional<Map<String, String>> tryHome(ToolContext context, Path jBangHome) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Optional<Map<String, String>> maybeExisting = Provisioners.loadMetadata(context, jBangHome);
        if (maybeExisting.isPresent()) {
            return maybeExisting;
        }

        // execute and discover information
        Map<String, String> metadata = new HashMap<>(Map.of(GradleProvider.HOME, jBangHome.toString()));
        ToolExecution.Builder exe = executionTemplate(context, metadata)
                .addArguments("-v")
                .stdOut(out)
                .stdErr(err);
        // TODO: maybe collect more info?
        // $ gradle -v
        //
        // ------------------------------------------------------------
        // Gradle 9.5.0
        // ------------------------------------------------------------
        //
        // Build time:    2026-04-28 12:05:30 UTC
        // Revision:      3fe117d68f3907790f3809f121aa36303a9151f8
        //
        // Kotlin:        2.3.20
        // Groovy:        4.0.29
        // Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
        // Launcher JVM:  21.0.11 (Eclipse Adoptium 21.0.11+10-LTS)
        // Daemon JVM:    /home/cstamas/.sdkman/candidates/java/21.0.11-tem (no Daemon JVM specified, using current Java
        // home)
        // OS:            Linux 6.19.14-200.fc43.x86_64 amd64
        //
        // $

        try {
            ToolHandle.Result result = executeTool(context, metadata, exe.build());
            if (result.success()) {
                String[] versions = out.toString().trim().split("\\s");
                String version = versions[2];
                return Optional.of(Map.of(
                        ToolHandler.TOOL_NAME,
                        GradleProvider.NAME,
                        ToolHandler.TOOL_VERSION,
                        version.trim(),
                        GradleProvider.HOME,
                        jBangHome.toString()));
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
            // TODO: discover
            version = "9.5.0";
            uri = String.format("https://services.gradle.org/distributions/gradle-%s-bin.zip", version);
            homePath = NAME + "-" + version;
        } else {
            version = requireNonNull(metadata.get(ToolHandler.TOOL_VERSION));
            uri = String.format("https://services.gradle.org/distributions/gradle-%s-bin.zip", version);
            homePath = NAME + "-" + version;
        }
        Path installDir = context.installationDirectory().resolve(homePath);
        try (FileUtils.TempFile dl = Provisioners.httpGet(context, "services.gradle.org", URI.create(uri))) {
            Provisioners.unpack(context, dl.getPath(), installDir, false);
        }
        Optional<Map<String, String>> provisioned = tryHome(context, installDir);
        if (provisioned.isPresent()) {
            Map<String, String> provisionedMetadata = new HashMap<>(provisioned.orElseThrow());
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
    public ToolExecution.Builder executionTemplate(ToolContext context, Map<String, String> metadata) {
        ToolExecution.Builder builder;
        String jbangHome = metadata.get(GradleProvider.HOME);
        if (jbangHome != null) {
            builder = ToolExecution.ofCommand(
                    Path.of(jbangHome).resolve("bin/" + GradleProvider.EXE_NAME).toString());
            builder.environmentVariable(ENV_HOME, jbangHome);
        } else {
            builder = ToolExecution.ofCommand(GradleProvider.EXE_NAME);
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
