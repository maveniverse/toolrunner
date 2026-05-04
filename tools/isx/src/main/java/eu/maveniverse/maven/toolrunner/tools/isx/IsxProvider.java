/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.isx;

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

/**
 * The ISX tool provider.
 */
public class IsxProvider implements ToolProvider, ToolDetector, ToolProvisioner, ToolExecutor {
    public static final String NAME = "isx";

    private static final String EXE_NAME = "isx";
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
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            return List.of();
        }

        ArrayList<Map<String, String>> detected = new ArrayList<>();

        // detect from path; if allowed
        if (context.allowPathDetection()) {
            Set<Path> paths =
                    OSTools.dereference(OSTools.which(IsxProvider.EXE_NAME).orElse(Set.of()));
            // consider only those ending with `bin/$EXE_NAME`
            for (Path executable : paths) {
                if (executable.toString().replace("\\", "/").endsWith("/bin/" + IsxProvider.EXE_NAME)) {
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
    protected Optional<Map<String, String>> tryHome(ToolContext context, Path isxHome) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Optional<Map<String, String>> maybeExisting = Provisioners.loadMetadata(context, isxHome);
        if (maybeExisting.isPresent()) {
            return maybeExisting;
        }

        // execute and discover information
        Map<String, String> metadata = new HashMap<>(Map.of(IsxProvider.HOME, isxHome.toString()));
        ToolExecution.Builder exe = executionTemplate(context, metadata)
                .addArguments("--version")
                .stdOut(out)
                .stdErr(err);
        // TODO: maybe collect more info?
        // $ isx --version
        // incus-spawn 0.1.18 (e9870e9fe9bd8f55431d9ac9fe1a1939108c2464)
        // incus client 6.23, server 6.23
        // native (GraalVM 25.0.3)

        try {
            ToolHandle.Result result = executeTool(context, metadata, exe.build());
            if (result.success()) {
                String[] versions = out.toString().trim().split(" ");
                String version = versions[1];
                return Optional.of(Map.of(
                        ToolHandler.TOOL_NAME,
                        IsxProvider.NAME,
                        ToolHandler.TOOL_VERSION,
                        version,
                        IsxProvider.HOME,
                        isxHome.toString()));
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
        // tool is linux only
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            return Optional.empty();
        }
        // this is very new tool, we just go for latest
        Path installDir = context.installationDirectory().resolve(NAME);

        boolean installationSuccess = false;
        try (FileUtils.TempFile dl = Provisioners.httpGet(
                context, "github", URI.create("https://raw.githubusercontent.com/Sanne/incus-spawn/main/get-isx.sh"))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            ToolExecution cmd = ToolExecution.ofCommand("cat")
                    .addArguments(dl.getPath().toString())
                    .addArguments("| sh")
                    .environmentVariable("INSTALL_DIR", installDir.toString())
                    .stdOut(out)
                    .stdErr(err)
                    .build();
            installationSuccess = ProcessBuilderExecutor.execute(cmd).success();
            if (!installationSuccess) {
                System.out.println(out);
            }
        } catch (InterruptedException e) {
            throw new IOException("Provisioning interrupted", e);
        }
        if (installationSuccess) {
            Optional<Map<String, String>> provisioned = tryHome(context, installDir);
            if (provisioned.isPresent()) {
                Map<String, String> provisionedMetadata = new HashMap<>(provisioned.orElseThrow());
                Path versionedInstallDir = context.installationDirectory()
                        .resolve(NAME + "-" + requireNonNull(provisionedMetadata.get(ToolHandler.TOOL_VERSION)));
                Files.move(installDir, versionedInstallDir, StandardCopyOption.REPLACE_EXISTING);
                provisionedMetadata.put(HOME, versionedInstallDir.toString());
                installDir = versionedInstallDir;
                Provisioners.saveMetadata(context, installDir, provisionedMetadata);
                return Optional.of(provisionedMetadata);
            }
        }
        return Optional.empty();
    }

    // ToolExecutor

    @Override
    public ToolExecution.Builder executionTemplate(ToolContext context, Map<String, String> metadata) {
        ToolExecution.Builder builder;
        String isxHome = metadata.get(IsxProvider.HOME);
        if (isxHome != null) {
            builder = ToolExecution.ofCommand(
                    Path.of(isxHome).resolve(IsxProvider.EXE_NAME).toString());
        } else {
            builder = ToolExecution.ofCommand(IsxProvider.EXE_NAME);
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
