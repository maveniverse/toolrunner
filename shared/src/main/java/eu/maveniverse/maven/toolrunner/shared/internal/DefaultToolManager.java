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
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseManager;
import eu.maveniverse.maven.nisse.source.osdetector.OsDetectorPropertySource;
import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultToolManager implements ToolManager, ToolContext {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Config config;
    private final Map<String, String> detectedOs;

    private final AtomicBoolean closed;
    private final Map<String, ToolProvider> toolProviders;

    public DefaultToolManager(Config config) throws IOException {
        this.config = requireNonNull(config);
        Files.createDirectories(config.installationDirectory());
        Files.createDirectories(config.tmpDirectory());

        this.detectedOs = Collections.unmodifiableMap(
                new SimpleNisseManager(Collections.singletonList(new OsDetectorPropertySource()))
                        .createProperties(SimpleNisseConfiguration.builder()
                                .withSystemProperties(System.getProperties())
                                .withPropertyKeyNamingStrategy(PropertyKeyNamingStrategies.prefixed(""))
                                .build()));

        this.closed = new AtomicBoolean(false);
        this.toolProviders = new HashMap<>();

        ServiceLoader.load(ToolProvider.class).iterator().forEachRemaining(p -> {
            toolProviders.put(p.name(), p);
        });
        log.debug(
                "Created tool manager installationDir={}, tempDir={}, toolProviders={}",
                config.installationDirectory(),
                config.tmpDirectory(),
                toolProviders.keySet());
    }

    // ToolContext

    @Override
    public Config config() {
        return config;
    }

    @Override
    public Map<String, String> detectedOs() {
        return detectedOs;
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
        return Collections.unmodifiableSet(new HashSet<>(toolProviders.keySet()));
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
            if (config.isTransient()) {
                FileUtils.deleteRecursively(config.installationDirectory());
            }
        }
    }
}
