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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultToolManager implements ToolManager, ToolContext {
    private final boolean isTransient;
    private final EffectiveConfig config;
    private final Context context;
    private final AtomicBoolean closed;
    private final List<ToolProvider> toolProviders;

    public DefaultToolManager(Config config) throws IOException {
        requireNonNull(config);
        this.isTransient = config.isTransient();

        Path installationDirectory =
                config.installationDirectory().orElse(Files.createTempDirectory("toolrunner-installation"));
        Path tempDirectory = config.tempDirectory().orElse(Files.createTempDirectory("toolrunner-temp"));
        String userAgent = config.userAgent()
                .orElse("ToolRunner/"
                        + MavenUtils.discoverArtifactVersion(
                                getClass().getClassLoader(), "eu.maveniverse.maven.toolrunner", "shared", "UNKNOWN"));
        Map<String, String> httpHeaders = config.httpHeaders().orElse(Map.of());
        this.config = new EffectiveConfig(installationDirectory, tempDirectory, userAgent, httpHeaders);
        this.context = Runtimes.INSTANCE
                .getRuntime()
                .create(ContextOverrides.create().withUserSettings(true).build());
        this.closed = new AtomicBoolean(false);
        this.toolProviders = new ArrayList<>();

        ServiceLoader.load(ToolProvider.class).iterator().forEachRemaining(toolProviders::add);
    }

    // ToolContext

    @Override
    public EffectiveConfig effectiveConfig() {
        return config;
    }

    @Override
    public Context context() {
        return context;
    }

    // ToolManager

    @Override
    public List<String> supportedToolNames() {
        return List.copyOf(toolProviders.stream().map(ToolProvider::name).toList());
    }

    @Override
    public Optional<ToolHandler> selectToolByName(String toolName) {
        return toolProviders.stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .map(p -> new DefaultToolHandler(this, p));
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            context.close();
            if (isTransient) {
                FileUtils.deleteRecursively(config.installationDirectory());
            }
        }
    }
}
