/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import java.util.List;
import java.util.Optional;

/**
 * The Tool Manager.
 */
public interface ToolManager {
    /**
     * List of tool names that manager supports (have providers detected as available).
     * This method never returns {@code null}.
     */
    List<String> supportedToolNames();

    /**
     * Selects a {@link ToolHandler} based on name, if available. This method never returns {@code null}.
     *
     * @param toolName the tool name, must not be {@code null}.
     */
    Optional<ToolHandler> selectToolByName(String toolName);
}
