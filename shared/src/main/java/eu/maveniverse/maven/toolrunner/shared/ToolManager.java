/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import eu.maveniverse.maven.toolrunner.shared.internal.DefaultToolManager;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * The Tool Manager.
 */
public interface ToolManager extends Closeable {
    /**
     * Factory for tool manager.
     */
    static ToolManager create(Config config) throws IOException {
        return new DefaultToolManager(config);
    }

    /**
     * List of tool names that manager supports (have providers detected as available).
     * This method never returns {@code null}.
     */
    Set<String> supportedToolNames();

    /**
     * Selects a {@link ToolHandler} based on name, if available. This method never returns {@code null}.
     *
     * @param toolName the tool name, must not be {@code null}.
     */
    Optional<ToolHandler> selectToolByName(String toolName);

    /**
     * Performs manual tool provider registration. Provider must not be {@code null}. If provider with same name
     * already existed. it will be replaced.
     */
    void registerProvider(ToolProvider provider);
}
