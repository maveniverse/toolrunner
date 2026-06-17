/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jdk;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import java.io.IOException;
import java.util.Map;

/**
 * The JDK tools that is no op in Java 8.
 */
class JdkTools {
    /**
     * Returns {@code true} if command is supported.
     */
    static boolean supportsTool(ToolContext context, Map<String, String> metadata, ToolExecution execution) {
        return false;
    }

    /**
     * Executes supported tool.
     */
    static ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
