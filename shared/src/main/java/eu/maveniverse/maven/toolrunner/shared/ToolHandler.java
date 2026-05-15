/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Tool Handler.
 */
public interface ToolHandler {

    /**
     * The tool prefix and keys in metadata.
     */
    String TOOL_PREFIX = "tool.";

    /**
     * Metadata key for tool name.
     */
    String TOOL_NAME = TOOL_PREFIX + "name";

    /**
     * Metadata key for tool version.
     */
    String TOOL_VERSION = TOOL_PREFIX + "version";

    /**
     * The tool metadata. Depending on tool, this may be some capability specifics and so on.
     * Two keys are always present:
     * <ul>
     *     <li>{@code tool.name} - the name of the tool represented by this handle</li>
     *     <li>{@code tool.version} - the version of the tool represented by this handle</li>
     * </ul>
     * This method never returns {@code null}.
     */
    List<Map<String, String>> detectTool();

    /**
     * Returns optionally a tool handle of belonging to specific discovered metadata. If passed in metadata is not
     * present/installed/matched, empty optional is returned. This method never returns {@code null}.
     *
     * @param metadata the tool metadata to select. It must not be {@code null}.
     */
    Optional<ToolHandle> selectTool(Map<String, String> metadata);

    /**
     * This method will always return a {@link ToolHandle}, even installing it is needed. Usually the "latest",
     * as defined by given tool, but it may be "latest stable" or like. Again, it depends on the tool provider.
     * If the given tool installation fails, or any other terminal problem occurs, an exception is thrown.
     * This method never returns {@code null}.
     *
     * @throws java.io.UncheckedIOException in case of any IO related issue (non-recoverable).
     * @throws java.lang.IllegalStateException in case of any non-IO related issue (non-recoverable).
     */
    ToolHandle toolHandle();
}
