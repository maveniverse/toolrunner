/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtimes;
import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.shared.core.maven.MavenUtils;
import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultToolManager implements ToolManager, ToolContext {
    private final boolean isTransient;
    private final String toolRunnerVersion;
    private final boolean allowPathDetection;
    private final Path installationDirectory;
    private final Path tempDirectory;
    private final String userAgent;
    private final Map<String, String> httpHeaders;

    private final AtomicBoolean closed;
    private final Map<String, ToolProvider> toolProviders;

    public DefaultToolManager(Config config) throws IOException {
        requireNonNull(config);
        this.isTransient = config.isTransient();
        this.toolRunnerVersion = MavenUtils.discoverArtifactVersion(
                getClass().getClassLoader(), "eu.maveniverse.maven.toolrunner", "shared", "UNKNOWN");
        this.allowPathDetection = config.allowPathDetection();
        this.tempDirectory =
                FileUtils.normalizePath(config.tempDirectory().orElse(Files.createTempDirectory("toolrunner-temp")));
        Files.createDirectories(this.tempDirectory);
        this.installationDirectory = FileUtils.normalizePath(config.installationDirectory()
                .orElse(
                        this.isTransient
                                ? Files.createTempDirectory(this.tempDirectory, "toolrunner-installation")
                                : FileUtils.discoverUserHomeDirectory().resolve(".toolrunner")));
        Files.createDirectories(this.installationDirectory);
        this.userAgent = config.userAgent().orElse("ToolRunner/" + this.toolRunnerVersion);
        this.httpHeaders = config.httpHeaders().orElse(Map.of());

        this.closed = new AtomicBoolean(false);
        this.toolProviders = new HashMap<>();

        ServiceLoader.load(ToolProvider.class).iterator().forEachRemaining(p -> {
            toolProviders.put(p.name(), p);
        });
    }

    // ToolContext

    @Override
    public String toolRunnerVersion() {
        return toolRunnerVersion;
    }

    @Override
    public boolean allowPathDetection() {
        return allowPathDetection;
    }

    @Override
    public Path installationDirectory() {
        return installationDirectory;
    }

    @Override
    public Path tempDirectory() {
        return tempDirectory;
    }

    @Override
    public String userAgent() {
        return userAgent;
    }

    @Override
    public Map<String, String> httpHeaders() {
        return httpHeaders;
    }

    @Override
    public Context createMimaContext() {
        return Runtimes.INSTANCE
                .getRuntime()
                .create(ContextOverrides.create().withUserSettings(true).build());
    }

    // ToolManager

    @Override
    public Set<String> supportedToolNames() {
        return Set.copyOf(toolProviders.keySet());
    }

    @Override
    public Optional<ToolHandler> selectToolByName(String toolName) {
        ToolProvider toolProvider = toolProviders.get(toolName);
        if (toolProvider == null) {
            return Optional.empty();
        }
        return Optional.of(new DefaultToolHandler(this, toolProvider));
    }

    @Override
    public void registerProvider(ToolProvider provider) {
        requireNonNull(provider);
        toolProviders.put(provider.name(), provider);
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            if (isTransient) {
                FileUtils.deleteRecursively(installationDirectory);
            }
            FileUtils.deleteRecursively(tempDirectory);
        }
    }
}
