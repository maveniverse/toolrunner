/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultToolHandle implements ToolHandle {
    private final ToolContext toolContext;
    private final Map<String, String> metadata;
    private final ToolExecutor toolExecutor;

    public DefaultToolHandle(ToolContext toolContext, Map<String, String> metadata, ToolExecutor toolExecutor) {
        this.toolContext = requireNonNull(toolContext);
        this.metadata = new HashMap<>(metadata);
        this.toolExecutor = requireNonNull(toolExecutor);
    }

    @Override
    public Map<String, String> toolMetadata() {
        return metadata;
    }

    @Override
    public List<String> commands() {
        return toolExecutor.commands(toolContext, metadata);
    }

    @Override
    public Result execute(ToolExecution execution) {
        try {
            return toolExecutor.executeTool(toolContext, metadata, execution);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
